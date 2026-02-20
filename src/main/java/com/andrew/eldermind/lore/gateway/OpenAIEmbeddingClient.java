package com.andrew.eldermind.lore.gateway;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OpenAIEmbeddingClient
 *
 * This class is responsible for interacting with OpenAI's Embeddings API.
 *
 * It lives in the "gateway" layer because:
 *   - It wraps an external API
 *   - It should not contain business logic
 *   - It isolates infrastructure concerns from retrieval strategy logic
 *
 * Retrieval classes (e.g., EmbeddingRetriever) should depend on the
 * EmbeddingClient interface, NOT directly on OpenAI.
 *
 * This keeps the system:
 *   - Testable (can mock EmbeddingClient)
 *   - Swappable (can replace OpenAI with local embedding model later)
 *   - Architecturally clean
 */
@Service
public class OpenAIEmbeddingClient implements EmbeddingClient {

    /**
     * OpenAIClient is injected by Spring from OpenAIConfig.
     * This ensures:
     *   - Single shared API client
     *   - Centralized API key configuration
     *   - No repeated client construction
     */
    private final OpenAIClient client;

    public OpenAIEmbeddingClient(OpenAIClient client) {
        this.client = client;
    }

    /**
     * Generates a semantic embedding vector for the provided text.
     *
     * Embeddings convert text into a high-dimensional numeric vector.
     * These vectors allow us to compute semantic similarity via cosine similarity.
     *
     * @param text The text to embed (e.g., lore snippet or user query)
     * @return List<Double> representing the embedding vector
     */
    @Override
    public List<Double> embed(String text) {

        // ------------------------------------------------------------
        // 1. Basic input validation
        // ------------------------------------------------------------
        // Embeddings API requires non-empty input.
        // We defensively trim and validate to avoid unnecessary API calls.
        String input = (text == null) ? "" : text.trim();

        if (input.isEmpty()) {
            throw new IllegalArgumentException(
                    "Embedding input text cannot be null or empty."
            );
        }

        // ------------------------------------------------------------
        // 2. Build embedding request parameters
        // ------------------------------------------------------------
        // We use the TEXT_EMBEDDING_3_SMALL model:
        //   - Optimized for semantic similarity
        //   - Lower cost than large model
        //   - Ideal for retrieval use cases
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .input(input)
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .build();

        // ------------------------------------------------------------
        // 3. Make synchronous API call to OpenAI
        // ------------------------------------------------------------
        // This performs a blocking network request.
        // For ElderMind’s current scale, synchronous is acceptable.
        // In production, you might:
        //   - Add retry logic
        //   - Add timeouts
        //   - Use async IO
        CreateEmbeddingResponse response = client.embeddings().create(params);

        // ------------------------------------------------------------
        // 4. Extract embedding vector and convert Float -> Double
        // ------------------------------------------------------------
        // The OpenAI SDK returns List<Float>.
        // Our domain model uses List<Double> for consistency
        // and better numeric precision in cosine similarity.
        //
        // We convert explicitly to avoid type mismatch issues.
        List<Float> floatEmbedding = response.data()
                .get(0)
                .embedding();

        return floatEmbedding.stream()
                .map(Float::doubleValue)
                .toList();

        }
    }
