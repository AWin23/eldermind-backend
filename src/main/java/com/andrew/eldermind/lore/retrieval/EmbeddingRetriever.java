package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;

import com.andrew.eldermind.lore.gateway.EmbeddingClient;
import org.springframework.stereotype.Service;

import com.andrew.eldermind.lore.corpus.LoreCorpusLoader;

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

    // In-Memory lore corpus used for retrieval.
    private final List<LoreDocument> corpus;

    // External gateway to generate embeddings (query-time).
    private final EmbeddingClient embeddingClient;

    public EmbeddingRetriever(LoreCorpusLoader corpusLoader, EmbeddingClient embeddingClient) {

        // Loads corpus into memory at startup.
        // This keeps retrieval fast and avoids re-reading JSON on every request.
        this.corpus = corpusLoader.load();
        this.embeddingClient = embeddingClient;
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
        System.out.println("[EmbeddingRetriever] corpus size=" + corpus.size());
        long embeddedCount = corpus.stream().filter(this::hasEmbedding).count();
        System.out.println("[EmbeddingRetriever] docs with embeddings=" + embeddedCount);

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
