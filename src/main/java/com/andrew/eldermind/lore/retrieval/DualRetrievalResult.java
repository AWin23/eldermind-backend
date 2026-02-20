package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.lore.corpus.LoreMatch;

import java.util.List;

/**
 * Wrapper result returned by DualLoreRetriever.
 * Carries:
 *  - decision metadata (RetrievalDecision)
 *  - the chosen retrieval matches (List<LoreMatch>)
 */
public class DualRetrievalResult {

    private final RetrievalDecision decision;
    private final List<LoreMatch> matches;

    public DualRetrievalResult(RetrievalDecision decision, List<LoreMatch> matches) {
        this.decision = decision;
        this.matches = matches;
    }

    public RetrievalDecision getDecision() {
        return decision;
    }

    public List<LoreMatch> getMatches() {
        return matches;
    }
}
