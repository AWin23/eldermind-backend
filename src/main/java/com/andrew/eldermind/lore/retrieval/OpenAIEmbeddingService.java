package com.andrew.eldermind.lore.retrieval;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    @Override
    public List<Double> embed(String text) {
        // call OpenAI embeddings endpoint (or stub for now)
        return List.of();
    }
}
