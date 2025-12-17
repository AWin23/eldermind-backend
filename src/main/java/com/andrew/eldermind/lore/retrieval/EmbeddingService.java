package com.andrew.eldermind.lore.retrieval;

import java.util.List;

public interface EmbeddingService {
    List<Double> embed(String text);
}
