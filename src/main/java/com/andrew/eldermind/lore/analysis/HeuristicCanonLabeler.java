package com.andrew.eldermind.lore.analysis;

import com.andrew.eldermind.lore.corpus.LoreDocument;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class HeuristicCanonLabeler implements CanonLabeler {
    @Override
    public String label(List<LoreDocument> evidence) {
        // source-based rules + disagreement detection
        return "Canon";
    }
}
