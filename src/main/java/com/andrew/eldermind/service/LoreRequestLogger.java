package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.dto.OutputValidationResult;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LoreRequestLogger {

    private static final Logger log =
            LoggerFactory.getLogger(LoreRequestLogger.class);

    public void logGate(String query, Set<String> mustHit) {
        log.info("==================================================");
        log.info(
                "LoreGate query=\"{}\" mustHit={}",
                safeQuery(query),
                mustHit
        );
        log.info("==================================================");
    }

    /**
     * Logs the retrieval summary, including top matched documents and their scores.
     */
    public void logRetrievalSummary(String query, List<LoreMatch> matches) {
        
        // If no matches, log that fact and return early.
        if (matches == null || matches.isEmpty()) {
            log.info("==================================================");
            log.info(
                    "LoreRetrieval query=\"{}\" matchedDocs=0",
                    safeQuery(query)
            );
            log.info("==================================================");
            return;
        }


        // Log the top match and total matched docs at INFO level, then log the full ranked list at DEBUG level.
        log.info("==================================================");
        log.info(
                "LoreRetrieval query=\"{}\" matchedDocs={} topScore={} topDoc=\"{}\"",
                safeQuery(query),
                matches.size(),
                round(matches.get(0).getScore()),
                safe(matches.get(0).getDocument().getTitle())
        );
        log.info("==================================================");


        // Log the full ranked list of matches at DEBUG level.
        for (int i = 0; i < matches.size(); i++) {
            LoreMatch match = matches.get(i);

            log.debug(
                    "LoreRetrievalRank rank={} id={} title=\"{}\" score={}",
                    i + 1,
                    safe(match.getDocument().getId()),
                    safe(match.getDocument().getTitle()),
                    round(match.getScore())
            );
        }
    }

    /**
     * Logs the retrieval decision, including whether retrieval was attempted and used, and the top matched document.
     */
    public void logDecision(RetrievalDecision decision, String query) {
        if (decision == null) return;

        log.info("==================================================");
        // Log the retrieval decision details at INFO level.
        log.info(
                "RetrievalDecision attempted={} used={} matchedDocs={} topScore={} threshold={} confidence={} groundingLabel=\"{}\" fallbackReason={} retrieverVersion={} query=\"{}\"",
                decision.isRetrievalAttempted(),
                decision.isRetrievalUsed(),
                decision.getMatchedDocs(),
                round(decision.getTopScore()),
                round(decision.getThreshold()),
                round(decision.getRetrievalConfidence()),
                safe(decision.getGroundingLabel()),
                decision.getFallbackReason(),
                decision.getRetrieverVersion(),
                safeQuery(query)
        );
        log.info("==================================================");
    }

    /**
     * Logs the validation result, including whether the output passed validation and any warnings.
     */
    public void logValidation(
            String query,
            RetrievalDecision decision,
            OutputValidationResult validation,
            int matchesCount
    ) {
        log.info("==================================================");
        // If validation is null, log that fact and return early.
        if (validation == null) return;

        // Log a concise summary at INFO level, then log detailed validation results at WARN level if validation failed.
        if (validation.isPassed()) {
            log.info(
                    "OutputValidation passed=true query=\"{}\" retrievalUsed={} confidence={} matchesCount={} warnings={}",
                    safeQuery(query),
                    decision != null && decision.isRetrievalUsed(),
                    decision == null ? "0.000" : round(decision.getRetrievalConfidence()),
                    matchesCount,
                    validation.getWarnings()
            );
            return;
        }

        // Log detailed validation results at WARN level if validation failed.
        log.warn(
                "OutputValidation passed=false query=\"{}\" retrievalUsed={} confidence={} matchesCount={} alignmentPassed={} confidencePassed={} fallbackPassed={} warnings={}",
                safeQuery(query),
                decision != null && decision.isRetrievalUsed(),
                decision == null ? "0.000" : round(decision.getRetrievalConfidence()),
                matchesCount,
                validation.isAlignmentPassed(),
                validation.isConfidencePassed(),
                validation.isFallbackPassed(),
                validation.getWarnings()
        );
    }

    /**
     * Returns a safe version of the query string, trimming it and truncating it if necessary.
     */
    private String safeQuery(String query) {
        
        // Handle null query and trim whitespace. If the query is very long, truncate it to 160 characters for logging.
        if (query == null) return "";

        // Trim leading/trailing whitespace.
        String safeQuery = query.trim();

        // If the query is very long, truncate it to 160 characters for logging.
        if (safeQuery.length() > 160) {
            safeQuery = safeQuery.substring(0, 160) + "...";
        }

        // Replace newlines with spaces for cleaner logging.
        return safeQuery;
    }

    /**
     * Returns a safe string for logging, replacing null with empty string.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Rounds a double value to three decimal places for logging.
     */
    private String round(double value) {

        // If the value is NaN or infinite, return it as-is to avoid formatting issues.
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }

        // Format the value to three decimal places for logging.
        return String.format(Locale.US, "%.3f", value);
    }
}