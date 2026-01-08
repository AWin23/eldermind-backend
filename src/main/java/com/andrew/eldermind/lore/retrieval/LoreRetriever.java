package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreMatch;

import java.util.List;

public interface LoreRetriever {
    List<LoreMatch> retrieveTopK(String query, int k);
}
