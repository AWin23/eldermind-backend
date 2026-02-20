package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.dto.FallbackReason;
import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.dto.DualRetrievalResult;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs BOTH keyword + embedding retrieval and chooses which result set to use.
 * This is your evaluation layer.
 */
@Service
public class DualLoreRetriever {

    private final KeywordRetriever keywordRetriever;
    private final EmbeddingRetriever embeddingRetriever;

    // Tune these later after seeing logs.
    private static final double KEYWORD_THRESHOLD = 1.0;
    private static final double EMBEDDING_THRESHOLD = 0.25;

    private static final String KEYWORD_VERSION = "keyword-v1";
    private static final String EMBEDDING_VERSION = "embedding-v1";

    public DualLoreRetriever(KeywordRetriever keywordRetriever,
                             EmbeddingRetriever embeddingRetriever) {
        this.keywordRetriever = keywordRetriever;
        this.embeddingRetriever = embeddingRetriever;
    }

    /**
     * Runs both retrievers, applies confidence gates, and returns:
     * - which one was chosen (decision metadata)
     * - the chosen matches
     */
    public DualRetrievalResult retrieveBest(String query, int k) {

        RetrievalDecision decision = new RetrievalDecision();
        decision.setRetrievalAttempted(true);

        // 1) Keyword retrieval
        List<LoreMatch> keywordMatches = keywordRetriever.retrieveTopK(query, k);
        double keywordTop = topScore(keywordMatches);

        // 2) Embedding retrieval
        // NOTE: until you do startup embedding bootstrap, this may often be empty.
        List<LoreMatch> embeddingMatches = embeddingRetriever.retrieveTopK(query, k);
        double embeddingTop = topScore(embeddingMatches);

        boolean keywordConfident = keywordTop >= KEYWORD_THRESHOLD;
        boolean embeddingConfident = embeddingTop >= EMBEDDING_THRESHOLD;

        // 3) Decision logic (simple + explainable)
        if (embeddingConfident && embeddingTop >= keywordTop) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(embeddingMatches.size());
            decision.setTopScore(embeddingTop);
            decision.setThreshold(EMBEDDING_THRESHOLD);
            decision.setFallbackReason(null);
            decision.setRetrieverVersion(EMBEDDING_VERSION);
            return new DualRetrievalResult(decision, embeddingMatches);
        }

        if (keywordConfident) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(keywordMatches.size());
            decision.setTopScore(keywordTop);
            decision.setThreshold(KEYWORD_THRESHOLD);
            decision.setFallbackReason(null);
            decision.setRetrieverVersion(KEYWORD_VERSION);
            return new DualRetrievalResult(decision, keywordMatches);
        }

        // Neither confident — best effort if we have *any* candidates
        if (!embeddingMatches.isEmpty()) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(embeddingMatches.size());
            decision.setTopScore(embeddingTop);
            decision.setThreshold(EMBEDDING_THRESHOLD);
            decision.setFallbackReason(FallbackReason.LOW_CONFIDENCE);
            decision.setRetrieverVersion(EMBEDDING_VERSION);
            return new DualRetrievalResult(decision, embeddingMatches);
        }

        if (!keywordMatches.isEmpty()) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(keywordMatches.size());
            decision.setTopScore(keywordTop);
            decision.setThreshold(KEYWORD_THRESHOLD);
            decision.setFallbackReason(FallbackReason.LOW_CONFIDENCE);
            decision.setRetrieverVersion(KEYWORD_VERSION);
            return new DualRetrievalResult(decision, keywordMatches);
        }

        // No matches at all — fallback
        decision.setRetrievalUsed(false);
        decision.setMatchedDocs(0);
        decision.setTopScore(0.0);
        decision.setThreshold(null);
        decision.setFallbackReason(FallbackReason.NO_MATCHES);
        decision.setRetrieverVersion(KEYWORD_VERSION);
        return new DualRetrievalResult(decision, List.of());
    }

    private double topScore(List<LoreMatch> matches) {
        if (matches == null || matches.isEmpty()) return Double.NEGATIVE_INFINITY;
        // Both retrievers sort descending, so index 0 is top.
        return matches.get(0).getScore();
    }
}
