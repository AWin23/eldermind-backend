package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 2: HybridRetriever
 *
 * Goal:
 *   Combine two different relevance signals:
 *     1) Keyword score (BM25-like / token-match style score)  -> unbounded: 0..∞
 *     2) Embedding cosine similarity (semantic match score)    -> bounded: usually ~0..1
 *
 * Why hybrid?
 *   - Keyword retrieval is precise when exact names appear (e.g., "Hlaalu", "Dagoth Ur")
 *   - Embedding retrieval catches semantic paraphrases (e.g., "what happened to..." vs "fall of...")
 *
 * Critical requirement:
 *   Keyword scores live on a different scale than cosine similarity.
 *   We MUST normalize keywordScore into [0,1] before mixing it with embeddingScore.
 *
 * Output:
 *   Returns List<LoreMatch> where LoreMatch.score = finalScore (0..1-ish).
 *   Everything downstream (Hard-evidence gate, prompt injection) can stay the same.
 */
@Service
public class HybridRetriever implements LoreRetriever {

    // Optional: if you want to keep more debug info per doc during merge, you can use this class.
    private static class HybridScoreRow {

    final LoreDocument doc; // for reference, not strictly needed for scoring

    // Original scores from retrievers (before normalization)
    double keywordRaw;
    double keywordNorm;
    double embeddingScore;
    double finalScore;

    // Constructor for convenience
    HybridScoreRow(LoreDocument doc) {
        this.doc = doc;
    }
}
    // Core retrievers we are hybridizing
    private final KeywordRetriever keywordRetriever;
    private final EmbeddingRetriever embeddingRetriever;

    /**
     * Weighting between signals.
     * Start with 0.6 semantic / 0.4 lexical as your “minimal safe” default.
     * You can tune later based on logs + false positives/negatives.
     */
    private static final double W_EMBEDDING = 0.6;
    private static final double W_KEYWORD = 0.4;


    /**
     * Keyword normalization hyperparameter.
     *
     * We use: keywordNorm = 1 - exp(-keywordScore / DIV)
     * - maps keywordScore from 0..∞ -> 0..1
     * - saturates quickly (prevents huge keyword scores from dominating)
     *
     * DIV=3.0 is a reasonable default; tune later based on keyword score distribution.
     */
    private static final double KEYWORD_NORM_DIV = 3.0;

    public HybridRetriever(KeywordRetriever keywordRetriever,
                           EmbeddingRetriever embeddingRetriever) {
        this.keywordRetriever = keywordRetriever;
        this.embeddingRetriever = embeddingRetriever;
    }


    /**
     * Retrieve top K lore snippets using hybrid retrieval and ranking.
     *
     * High-level pipeline:
     *
     *   1) Candidate Retrieval
     *      - Run both KeywordRetriever and EmbeddingRetriever.
     *      - Each retriever returns up to fetchK candidates (k * 3) to give the
     *        hybrid stage enough overlap for meaningful fusion.
     *
     *   2) Candidate Merge
     *      - Merge results by document ID so each document has a combined record
     *        containing:
     *            keywordScore (lexical relevance)
     *            embeddingScore (semantic similarity)
     *
     *   3) Score Normalization
     *      - Normalize keywordScore into the range [0,1] so it is comparable to
     *        cosine similarity embedding scores.
     *
     *   4) Hybrid Score Fusion
     *      - Compute the final hybrid ranking score using a weighted sum:
     *
     *          finalScore = (W_EMBEDDING * embeddingScore)
     *                     + (W_KEYWORD   * keywordNorm)
     *
     *      - This allows semantic similarity to dominate while still benefiting
     *        from precise keyword matches.
     *
     *   5) Ranking
     *      - Build internal HybridScoreRow objects containing:
     *            raw keyword score
     *            normalized keyword score
     *            embedding similarity
     *            final hybrid score
     *
     *      - Sort documents by finalScore descending.
     *      - Select the top K results.
     *
     *   6) Observability / Debug Logging
     *      - Log a detailed breakdown of the top ranked documents including:
     *            document id
     *            title
     *            raw keyword score
     *            normalized keyword score
     *            embedding similarity
     *            final hybrid score
     *
     *      - This makes it easy to inspect why a document ranked highly when
     *        tuning retrieval weights or debugging ranking behavior.
     *
     *   7) Output Conversion
     *      - Convert HybridScoreRow objects back into LoreMatch instances so the
     *        rest of the pipeline remains unchanged.
     *
     * Design Note:
     *   HybridRetriever intentionally performs only retrieval and ranking.
     *   Safety mechanisms such as the "hard-evidence gate" and threshold checks
     *   belong in DefaultLoreOrchestrator so this component stays pure and
     *   reusable.
     */
    @Override
    public List<LoreMatch> retrieveTopK(String query, int k) {

        // We fetch more than k from each retriever to give the merge room to work.
        // If keyword & embedding return mostly different docs, fetching only k
        // can produce a weak union and miss high-quality combined candidates.
        int fetchK = Math.max(k * 3, k);

        // 1) Run both retrievers
        List<LoreMatch> keywordMatches = keywordRetriever.retrieveTopK(query, fetchK);
        List<LoreMatch> embeddingMatches = embeddingRetriever.retrieveTopK(query, fetchK);

        // 2) Merge by docId
        // docId -> partial scores (keyword + embedding)
        Map<String, PartialScores> mergedById = new HashMap<>();

        // Add keyword scores
        for (LoreMatch m : keywordMatches) {
            LoreDocument doc = m.getDocument();
            String docId = safeId(doc);
            if (docId == null) continue;

            mergedById
                    .computeIfAbsent(docId, ignored -> new PartialScores(doc))
                    .keywordScore = m.getScore();
        }

        // Add embedding scores
        for (LoreMatch m : embeddingMatches) {
            LoreDocument doc = m.getDocument();
            String docId = safeId(doc);
            if (docId == null) continue;

            mergedById
                    .computeIfAbsent(docId, ignored -> new PartialScores(doc))
                    .embeddingScore = m.getScore();
        }

        // 3) Build detailed score rows so we can log all score components
        List<HybridScoreRow> rankedRows = mergedById.values().stream()
                .map(p -> {
                    HybridScoreRow row = new HybridScoreRow(p.doc);
                    row.keywordRaw = p.keywordScore;
                    row.keywordNorm = normalizeKeyword(p.keywordScore);
                    row.embeddingScore = p.embeddingScore;
                    row.finalScore =
                            (W_EMBEDDING * row.embeddingScore) +
                            (W_KEYWORD * row.keywordNorm);
                    return row;
                })
                .sorted(Comparator.comparingDouble((HybridScoreRow row) -> row.finalScore).reversed())
                .limit(k)
                .collect(Collectors.toList());

        // 4) Rich hybrid debug logging
        if (!rankedRows.isEmpty()) {
            System.out.println(
                    "[HybridRetriever] query=\"" + query + "\" " +
                    "candidates=" + mergedById.size() + " " +
                    "topFinal=" + round(rankedRows.get(0).finalScore) + " " +
                    "topKwRaw=" + round(topScore(keywordMatches)) + " " +
                    "topEmb=" + round(topScore(embeddingMatches))
            );

            for (int i = 0; i < rankedRows.size(); i++) {
                HybridScoreRow row = rankedRows.get(i);

                System.out.println(
                        "  rank=" + (i + 1) +
                        " id=" + safeId(row.doc) +
                        " | title=" + safe(row.doc.getTitle())
                );

                System.out.println(
                        "    kwRaw=" + round(row.keywordRaw) +
                        " kwNorm=" + round(row.keywordNorm) +
                        " emb=" + round(row.embeddingScore) +
                        " final=" + round(row.finalScore)
                );
            }
        } else {
            System.out.println(
                    "[HybridRetriever] query=\"" + query + "\" no results " +
                    "(kw=" + keywordMatches.size() + ", emb=" + embeddingMatches.size() + ")"
            );
        }

        // 5) Convert back to LoreMatch so downstream stays unchanged
        return rankedRows.stream()
                .map(row -> new LoreMatch(row.doc, row.finalScore))
                .collect(Collectors.toList());
    }

    /**
     * Normalize keyword score (0..∞) -> (0..1).
     *
     * Why this specific function?
     * - Smoothly increases for small keyword scores
     * - Saturates toward 1.0 for larger scores
     * - Prevents keyword from dominating the hybrid score
     *
     * If keywordScore = 0 -> 0
     * If keywordScore grows large -> approaches 1
     */
    private double normalizeKeyword(double keywordScore) {
        if (keywordScore <= 0.0) return 0.0;
        return 1.0 - Math.exp(-keywordScore / KEYWORD_NORM_DIV);
    }

    
    // Defensive string handling for debug logs (avoid nulls).
    private String safe(String value) {
        return value == null ? "" : value;
    }
    

    /**
     * Defensive doc id extraction.
     * Hybrid merging requires a stable unique key per document.
     */
    private String safeId(LoreDocument doc) {
        if (doc == null) return null;
        String id = doc.getId();
        return (id == null || id.isBlank()) ? null : id;
    }

    /**
     * Utility: get the highest score from a list (for debug logs).
     */
    private double topScore(List<LoreMatch> matches) {
        if (matches == null || matches.isEmpty()) return Double.NEGATIVE_INFINITY;
        return matches.get(0).getScore();
    }

    /**
     * Debug helper (avoid noisy decimals in console output).
     */
    private String round(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v)) return String.valueOf(v);
        return String.format(Locale.US, "%.3f", v);
    }

    /**
     * Holds partial scores for a single doc during merge.
     * Missing scores default to 0.0 (meaning: that signal did not retrieve this doc).
     */
    private static class PartialScores {
        final LoreDocument doc;
        double keywordScore = 0.0;
        double embeddingScore = 0.0;

        PartialScores(LoreDocument doc) {
            this.doc = doc;
        }
    }
}