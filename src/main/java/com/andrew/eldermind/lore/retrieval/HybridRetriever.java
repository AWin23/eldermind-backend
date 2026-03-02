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
     * Retrieve top K lore snippets using hybrid scoring.
     *
     * High-level steps:
     *   1) Pull candidates from KeywordRetriever and EmbeddingRetriever
     *   2) Merge results by docId so each doc has (keywordScore, embeddingScore)
     *   3) Normalize keywordScore into [0,1]
     *   4) Compute finalScore using weighted sum
     *   5) Sort by finalScore descending, return top K
     *
     * Note:
     *   Your "hard-evidence gate" belongs AFTER ranking (in orchestrator),
     *   not inside this retriever, so HybridRetriever stays pure: retrieval+ranking only.
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
        // docId -> Partial scores (keyword + embedding)
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

        // 3) Compute final hybrid score per doc
        // finalScore = 0.6 * embeddingScore + 0.4 * keywordNorm
        List<LoreMatch> hybridRanked = mergedById.values().stream()
                .map(p -> {
                    double keywordNorm = normalizeKeyword(p.keywordScore);
                    double finalScore =
                            (W_EMBEDDING * p.embeddingScore) +
                            (W_KEYWORD * keywordNorm);

                    // Return LoreMatch with finalScore so downstream stays identical
                    return new LoreMatch(p.doc, finalScore);
                })
                .sorted(Comparator.comparingDouble(LoreMatch::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());

        // Optional: lightweight debug logging (useful when tuning)
        // You can replace System.out with your logger.
        if (!hybridRanked.isEmpty()) {
            System.out.println(
                    "[HybridRetriever] query=\"" + query + "\" " +
                    "candidates=" + mergedById.size() + " " +
                    "topFinal=" + round(hybridRanked.get(0).getScore()) + " " +
                    "topKwRaw=" + round(topScore(keywordMatches)) + " " +
                    "topEmb=" + round(topScore(embeddingMatches))
            );
        } else {
            System.out.println(
                    "[HybridRetriever] query=\"" + query + "\" no results " +
                    "(kw=" + keywordMatches.size() + ", emb=" + embeddingMatches.size() + ")"
            );
        }

        return hybridRanked;
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