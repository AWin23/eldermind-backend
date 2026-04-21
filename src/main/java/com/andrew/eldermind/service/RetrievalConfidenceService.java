package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.RetrievalDecision;
import org.springframework.stereotype.Service;

@Service
// Service to compute retrieval confidence and labels based on the RetrievalDecision data
public class RetrievalConfidenceService {
    public double computeConfidence(RetrievalDecision decision) {

        // If retrieval was not used or threshold is missing, confidence is 0
        if (!decision.isRetrievalUsed() || decision.getThreshold() == null) {
            return 0.0;
        }

        // Compute confidence based on top score, threshold, and number of matched documents
        double topScore = decision.getTopScore();
        double threshold = decision.getThreshold();
        int matchedDocs = decision.getMatchedDocs();

        // Define a "strong" score as either 0.35 above the threshold or double the threshold, whichever is higher
        double strongScore = Math.max(threshold + 0.55, threshold * 3.0);

        // Normalize the top score to a 0-1 range based on the threshold and strong score
        double normalized = (topScore - threshold) / (strongScore - threshold);
        normalized = Math.max(0.0, Math.min(1.0, normalized));

        // Add a small bonus for having multiple matched documents (DEBUGGING)
        double docBonus = Math.min(0.10, matchedDocs * 0.03);

        // Final confidence is the normalized score plus the document bonus, capped at 1.0
        return Math.min(1.0, normalized + docBonus);
    }

    // Compute a human-readable label based on the confidence score
    public String computeLabel(double confidence) {
        if (confidence == 0.0) return "Answer generated without external evidence";
        if (confidence < 0.45) return "Weak supporting evidence";
        if (confidence < 0.80) return "Partially grounded in canonical sources";
        return "Grounded in canonical sources";
    }
}