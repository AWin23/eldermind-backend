package com.andrew.eldermind.lore.retrieval;

import java.util.Set;

public interface QueryAnalyzer {
    Set<String> extractKeywords(String query);
}
