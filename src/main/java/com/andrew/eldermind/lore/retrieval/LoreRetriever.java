package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import java.util.List;

public interface LoreRetriever {
    List<LoreDocument> retrieveTopK(String query, int k);
}
