package com.andrew.eldermind.dto;

import com.andrew.eldermind.lore.corpus.LoreMatch;

import java.util.List;

/**
 * DualRetrievalResult
 *
 * A small wrapper that carries:
 *  - the explainability/telemetry decision (RetrievalDecision DTO)
 *  - the actual chosen matches (List<LoreMatch>)
 *
 * We keep matches OUT of the DTO to avoid mixing transport concerns
 * with retrieval execution details.
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
