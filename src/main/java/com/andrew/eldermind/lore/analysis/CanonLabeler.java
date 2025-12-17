package com.andrew.eldermind.lore.analysis;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import java.util.List;

public interface CanonLabeler {
    String label(List<LoreDocument> evidence);
}
