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

        // Log the scores and confidence for analysis (tune thresholds later).
        System.out.println("[DualLoreRetriever] keywordTop=" + keywordTop +
                   " embeddingTop=" + embeddingTop +
                   " keywordConfident=" + keywordConfident +
                   " embeddingConfident=" + embeddingConfident);

        // ------------------------------------------------------------
        // 3) Decision logic (explainable + safe)
        // ------------------------------------------------------------
        // IMPORTANT: keyword scores and embedding scores are on different scales.
        // We should NOT compare them directly (e.g., embeddingTop >= keywordTop).
        //
        // Instead we use a "precision-first" policy:
        // - If keyword retrieval is confident, prefer it (exact matches, lower hallucination risk).
        // - Otherwise, if embeddings are confident, use semantic retrieval (better recall).
        // - Otherwise, fall back to chat-only (or optionally use low-confidence keyword matches).
        if (keywordConfident) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(keywordMatches.size());
            decision.setTopScore(keywordTop);
            decision.setThreshold(KEYWORD_THRESHOLD);
            decision.setFallbackReason(null);
            decision.setRetrieverVersion(KEYWORD_VERSION);
            return new DualRetrievalResult(decision, keywordMatches);
        }

        if (embeddingConfident) {
            decision.setRetrievalUsed(true);
            decision.setMatchedDocs(embeddingMatches.size());
            decision.setTopScore(embeddingTop);
            decision.setThreshold(EMBEDDING_THRESHOLD);
            decision.setFallbackReason(null);
            decision.setRetrieverVersion(EMBEDDING_VERSION);
            return new DualRetrievalResult(decision, embeddingMatches);
        }

        // ------------------------------------------------------------
        // 4) Not confident enough to ground safely → fallback
        // ------------------------------------------------------------
        // If you want "best effort" behavior, you could return keywordMatches here,
        // but safest is: don't inject weak evidence.
        decision.setRetrievalUsed(false);
        decision.setMatchedDocs(0);
        decision.setTopScore(0.0);
        decision.setThreshold(null);
        decision.setFallbackReason(FallbackReason.BELOW_THRESHOLD);
        decision.setRetrieverVersion(KEYWORD_VERSION);
        return new DualRetrievalResult(decision, List.of());
    }

    private double topScore(List<LoreMatch> matches) {
        if (matches == null || matches.isEmpty()) return Double.NEGATIVE_INFINITY;
        // Both retrievers sort descending, so index 0 is top.
        return matches.get(0).getScore();
    }
}
