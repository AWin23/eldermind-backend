package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;

import com.andrew.eldermind.lore.gateway.EmbeddingClient;
import com.andrew.eldermind.service.EmbeddingStatusService;

import org.springframework.stereotype.Service;

import com.andrew.eldermind.lore.corpus.LoreCorpusStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * EmbeddingRetriever
 *
 * Retrieval Strategy: Semantic (Vector) Retrieval
 *
 * This retriever ranks documents based on cosine similarity between:
 *   - the embedding vector of the user's query (computed at runtime)
 *   - the embedding vector of each lore document (precomputed and stored on the doc)
 *
 * IMPORTANT:
 * - This does NOT replace keyword retrieval.
 * - It is designed to be swappable via the LoreRetriever interface.
 *
 * Assumptions:
 * - LoreDocument.embedding is already populated (or at least for most docs).
 * - Startup bootstrap will fill missing embeddings later.
 */
@Service
public class EmbeddingRetriever implements LoreRetriever {

    // Logger for monitoring the bootstrapping process
    private static final Logger log =
        LoggerFactory.getLogger(EmbeddingRetriever.class);

    // In-Memory lore corpus used for retrieval.
    private final List<LoreDocument> corpus;

    // External gateway to generate embeddings (query-time).
    private final EmbeddingClient embeddingClient;

    // Service to track embedding readiness status
    private final EmbeddingStatusService embeddingStatusService;

    public EmbeddingRetriever(LoreCorpusStore corpusStore, EmbeddingClient embeddingClient, EmbeddingStatusService embeddingStatusService) {

        // Loads corpus into memory at startup.
        // This keeps retrieval fast and avoids re-reading JSON on every request.
        this.corpus = corpusStore.getCorpus();
        this.embeddingClient = embeddingClient;
        this.embeddingStatusService = embeddingStatusService;
    }

    /**
     * Retrieve the top K lore documents most semantically similar to the query.
     *
     * @param query user query text
     * @param k how many matches to return
     * @return top K LoreMatch results sorted by similarity (descending)
     */
    @Override
    public List<LoreMatch> retrieveTopK(String query, int k) {


        // Prevent retrieval from running against a partially-built embedding corpus.
        // During startup, EmbeddingBootstrapper may still be generating vectors.
        // Returning an empty list allows the orchestrator to cleanly fallback.
        if (!embeddingStatusService.isReady()) {

            log.warn(
                "EmbeddingRetriever called before bootstrap complete query=\"{}\"",
                query
            );

            return List.of();
        }

        // ------------------------------------------------------------
        // 1) Embed the user's query (runtime cost per request)
        // ------------------------------------------------------------
        // This produces a semantic vector representation of the query text.
        // Later, we can add caching for repeated queries if needed.
        List<Double> queryVec = embeddingClient.embed(query);

        // ------------------------------------------------------------
        // 2) Score each document by cosine similarity
        // ------------------------------------------------------------
        // We only score documents that actually have embeddings available.
        // Missing embeddings will be handled in the startup bootstrap later.
        List<LoreMatch> scored = corpus.stream()
                .filter(doc -> hasEmbedding(doc))
                .map(doc -> {
                    double sim = cosineSimilarity(queryVec, doc.getEmbedding());
                    return new LoreMatch(doc, sim);
                })
                .sorted(Comparator.comparingDouble(LoreMatch::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());

        
        // --- DEV LOGGING: check corpus and embedding availability ---
        long embeddedCount = corpus.stream()
        .filter(this::hasEmbedding)
        .count();

        log.info("==================================================");
        log.info(
                "EmbeddingRetriever.retrieveTopK query=\"{}\" corpusSize={} embeddedDocs={} returnedMatches={}",
                query,
                corpus.size(),
                embeddedCount,
                scored.size()
        );
        log.info("==================================================");

        embeddingStatusService.markReady(); // Mark embeddings as ready after first retrieval attempt (for testing/logging)

        // ------------------------------------------------------------
        // 3) Fallback behavior
        // ------------------------------------------------------------
        // If we have ZERO embedded documents (e.g., embeddings not built yet),
        // return an empty list and let the orchestrator decide how to fallback.
        // (We do NOT want to silently “make up” results here.)
        return scored;
    }

    // ------------------------------------------------------------
    // Helper: check whether a document has a usable embedding vector
    // ------------------------------------------------------------
    private boolean hasEmbedding(LoreDocument doc) {
        return doc != null
                && doc.getEmbedding() != null
                && !doc.getEmbedding().isEmpty();
    }

    // ------------------------------------------------------------
    // Helper: cosine similarity
    // ------------------------------------------------------------
    // Cosine similarity = dot(a,b) / (||a|| * ||b||)
    //
    // Notes:
    // - Output is typically in [-1, 1]
    // - For embeddings, related texts often land around ~0.2 to 0.8
    // - We add a small epsilon to avoid division-by-zero.
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        Objects.requireNonNull(a, "Vector A (query embedding) cannot be null");
        Objects.requireNonNull(b, "Vector B (doc embedding) cannot be null");

        if (a.size() != b.size()) {
            // Dimension mismatch usually means:
            // - embeddings were generated using different models
            // - or a corrupted/incomplete vector was stored
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: queryVec=" + a.size() + " docVec=" + b.size()
            );
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);

            dot += x * y;
            normA += x * x;
            normB += y * y;
        }

        double denom = Math.sqrt(normA) * Math.sqrt(normB);

        // Epsilon prevents divide-by-zero if a vector is all zeros (should not happen, but safe).
        double eps = 1e-12;

        return dot / (denom + eps);
    }
}
