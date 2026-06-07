package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import com.andrew.eldermind.lore.corpus.LoreCorpusStore;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG-lite retriever that ranks lore snippets using
 * lightweight keyword/entity matching.
 *
 * Later versions will add embedding similarity.
 */
@Service
public class KeywordRetriever implements LoreRetriever {

    //-- DEV NOTE: This is a very basic retriever for v1, focused on keyword matching.
    private static final Logger log = LoggerFactory.getLogger(KeywordRetriever.class);

    private final List<LoreDocument> corpus;
    private final QueryAnalyzer queryAnalyzer;

        public KeywordRetriever(
            LoreCorpusStore corpusStore,
            QueryAnalyzer queryAnalyzer
    ) {
        // Load corpus once at startup
        this.corpus = corpusStore.getCorpus();
        this.queryAnalyzer = queryAnalyzer;

        // Startup sanity check
        log.info("==================================================");
        log.info(
                "KeywordRetriever loaded corpusSize={}",
                this.corpus.size()
        );
        log.info("==================================================");

        if (!this.corpus.isEmpty()) {
            LoreDocument first = this.corpus.get(0);

            log.debug(
                    "KeywordRetriever firstDoc id={} title=\"{}\" preview=\"{}\"",
                    first.getId(),
                    first.getTitle(),
                    safe(first.getText()).substring(
                            0,
                            Math.min(80, safe(first.getText()).length())
                    )
            );
        } else {
            log.warn(
                    "KeywordRetriever corpus is empty. Check resources path lore/lore_corpus.json"
            );
        }
    }


    @Override
    public List<LoreMatch> retrieveTopK(String query, int k) {

        // --- Step A: extract keywords from query ---
        Set<String> keywords = queryAnalyzer.extractKeywords(query);

        // --- Step B: score ALL documents ---
        List<ScoredLoreDocument> scoredDocs = corpus.stream()
                .map(doc -> new ScoredLoreDocument(doc, score(doc, keywords)))
                .sorted(Comparator.comparingDouble(ScoredLoreDocument::score).reversed())
                .collect(Collectors.toList());

        // --- Step C: DEBUG LOGGING (Sprint 2 dev only) ---
                log.debug(
                "KeywordRetriever query=\"{}\" keywords={} candidates={}",
                safe(query),
                keywords,
                scoredDocs.size()
        );

        scoredDocs.stream().limit(k).forEach(s ->
                log.debug(
                        "KeywordCandidate id={} title=\"{}\" score={}",
                        s.doc().getId(),
                        s.doc().getTitle(),
                        s.score()
                )
        );

        // --- Step D: take only positive-score matches ---
        List<LoreMatch> topMatches = scoredDocs.stream()
                .filter(s -> s.score() > 0)
                .limit(k)
                .map(s -> new LoreMatch(s.doc(), s.score()))
                .collect(Collectors.toList());

        // --- Step E: FALLBACK if nothing matched ---
        if (topMatches.isEmpty()) {
            System.out.println("No positive-score matches found — falling back to top K documents.");

            topMatches = scoredDocs.stream()
                    .limit(k)
                    .map(s -> new LoreMatch(s.doc(), s.score()))
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
