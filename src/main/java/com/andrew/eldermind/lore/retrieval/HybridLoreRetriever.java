package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreCorpusLoader;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG-lite retriever that ranks lore snippets using
 * lightweight keyword/entity matching.
 *
 * Later versions will add embedding similarity.
 */
@Service
public class HybridLoreRetriever implements LoreRetriever {

    private final List<LoreDocument> corpus;
    private final QueryAnalyzer queryAnalyzer;

        public HybridLoreRetriever(
            LoreCorpusLoader corpusLoader,
            QueryAnalyzer queryAnalyzer
    ) {
        // Load corpus once at startup
        this.corpus = List.copyOf(corpusLoader.load());
        this.queryAnalyzer = queryAnalyzer;

        // --- Startup sanity check (dev-only) ---
        System.out.println("=== Lore Corpus Startup Check ===");
        System.out.println("Loaded lore corpus size: " + this.corpus.size());

        if (!this.corpus.isEmpty()) {
            LoreDocument first = this.corpus.get(0);
            System.out.println("First doc id: " + first.getId());
            System.out.println("First doc title: " + first.getTitle());
            System.out.println("First doc text preview: "
                    + safe(first.getText()).substring(0, Math.min(80, safe(first.getText()).length())));
        } else {
            System.out.println("WARNING: corpus is empty. Check resources path lore/lore_corpus.json");
        }
    }


    @Override
    public List<LoreDocument> retrieveTopK(String query, int k) {

        // --- Step A: extract keywords from query ---
        Set<String> keywords = queryAnalyzer.extractKeywords(query);

        // --- Step B: score ALL documents ---
        List<ScoredLoreDocument> scoredDocs = corpus.stream()
                .map(doc -> new ScoredLoreDocument(doc, score(doc, keywords)))
                .sorted(Comparator.comparingDouble(ScoredLoreDocument::score).reversed())
                .collect(Collectors.toList());

        // --- Step C: DEBUG LOGGING (Sprint 2 dev only) ---
        System.out.println("=== Lore Retrieval Debug ===");
        System.out.println("Query: " + query);
        System.out.println("Keywords: " + keywords);
        scoredDocs.stream().limit(k).forEach(s ->
                System.out.println(
                        "Candidate: " + s.doc().getId()
                        + " | title=" + s.doc().getTitle()
                        + " | score=" + s.score()
                )
        );

        // --- Step D: take only positive-score matches ---
        List<LoreDocument> topMatches = scoredDocs.stream()
                .filter(s -> s.score > 0)
                .limit(k)
                .map(ScoredLoreDocument::doc)
                .collect(Collectors.toList());

        // --- Step E: FALLBACK if nothing matched ---
        if (topMatches.isEmpty()) {
            System.out.println("No positive-score matches found — falling back to top K documents.");
            topMatches = scoredDocs.stream()
                    .limit(k)
                    .map(ScoredLoreDocument::doc)
                    .collect(Collectors.toList());
        }

        return topMatches;
    }


    /**
     * Simple keyword-based scoring.
     * Each keyword match increases the score.
     */
    private double score(LoreDocument doc, Set<String> keywords) {
    double score = 0.0;

    String title = safe(doc.getTitle()).toLowerCase();
    String body  = safe(doc.getText()).toLowerCase();

    for (String kw : keywords) {
        String k = kw.toLowerCase();
        if (title.contains(k)) score += 2.0;
        if (body.contains(k))  score += 1.0;
    }
    return score;
}

private String safe(String s) { return s == null ? "" : s; }
    /**
     * Internal helper record for sorting results.
     */
    private record ScoredLoreDocument(LoreDocument doc, double score) {}
}
