package com.andrew.eldermind.lore.gateway;

import java.util.List;

public interface EmbeddingClient {
    List<Double> embed(String text);
}
