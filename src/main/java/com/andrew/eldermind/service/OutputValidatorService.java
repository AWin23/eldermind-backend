package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import com.andrew.eldermind.dto.OutputValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OutputValidatorService {

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.55; // This threshold can be tuned based on retrieval confidence scoring.

    /**
     * Validates the final LLM response after retrieval + generation.
     *
     * This class is intentionally lightweight for v1:
     * - It does NOT block responses.
     * - It only produces warnings.
     * - It helps detect possible hallucinations, overconfidence, or fallback misuse.
     */
    public OutputValidationResult validate(
            String query,
            String response,
            RetrievalDecision decision,
            List<LoreMatch> matches
    ) {
        List<String> warnings = new ArrayList<>();

        boolean alignmentPassed = checkEvidenceAlignment(query, response, decision, matches, warnings);
        boolean confidencePassed = checkConfidenceMismatch(response, decision, warnings);
        boolean fallbackPassed = checkFallbackAwareness(response, decision, warnings);

        boolean passed = warnings.isEmpty();

        return new OutputValidationResult(
                passed,
                warnings,
                alignmentPassed,
                confidencePassed,
                fallbackPassed
        );
    }

    /**
     * Checks whether the response appears aligned with retrieved evidence.
     *
     * Basic idea:
     * If retrieval was used, the response should contain at least some important
     * words from the query or retrieved snippets.
     */
    private boolean checkEvidenceAlignment(
            String query,
            String response,
            RetrievalDecision decision,
            List<LoreMatch> matches,
            List<String> warnings
    ) {
        if (decision == null || !decision.isRetrievalUsed()) {
            return true;
        }

        if (response == null || response.isBlank()) {
            warnings.add("Response is empty even though retrieval was used.");
            return false;
        }

        String normalizedResponse = normalize(response);

        // v1 simple approach: check query terms first.
        List<String> queryTerms = extractKeyTerms(query);

        boolean responseContainsQueryTerm = queryTerms.stream()
                .anyMatch(normalizedResponse::contains);

        if (!responseContainsQueryTerm) {
            warnings.add("Potential hallucination: response may not align with key query terms.");
            return false;
        }

        return true;
    }

    /**
     * Checks whether a low-confidence retrieval result produced an overly confident answer.
     */
    private boolean checkConfidenceMismatch(
            String response,
            RetrievalDecision decision,
            List<String> warnings
    ) {
        if (decision == null || response == null) {
            return true;
        }

        double confidence = decision.getRetrievalConfidence();

        if (confidence < LOW_CONFIDENCE_THRESHOLD && responseLooksConfident(response)) {
            warnings.add("Overconfident response despite low retrieval confidence.");
            return false;
        }

        return true;
    }

    /**
     * Checks whether fallback cases are handled carefully.
     *
     * If fallbackReason exists, the model should not sound overly certain.
     */
    private boolean checkFallbackAwareness(
            String response,
            RetrievalDecision decision,
            List<String> warnings
    ) {
        if (decision == null || response == null) {
            return true;
        }

        if (decision.getFallbackReason() != null && responseLooksConfident(response)) {
            warnings.add("Response may be too certain despite fallback reason: "
                    + decision.getFallbackReason());
            return false;
        }

        return true;
    }

    /**
     * Very simple confidence detector.
     *
     * This is intentionally heuristic-based for v1.
     */
    private boolean responseLooksConfident(String response) {
        String normalized = normalize(response);

        return normalized.contains("definitely")
                || normalized.contains("clearly")
                || normalized.contains("without question")
                || normalized.contains("canonically")
                || normalized.contains("the answer is")
                || normalized.contains("it is known")
                || normalized.contains("there is no doubt");
    }

    /**
     * Extracts simple meaningful terms from the query.
     *
     * Later, you can replace this with your existing QueryAnalyzer.
     */
    private List<String> extractKeyTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = normalize(text);

        return List.of(normalized.split("\\s+")).stream()
                .filter(term -> term.length() > 3)
                .filter(term -> !isStopWord(term))
                .toList();
    }

    private boolean isStopWord(String term) {
        return List.of(
                "what", "when", "where", "which", "who", "why", "how",
                "does", "did", "was", "were", "about", "tell", "explain",
                "happened", "thing", "stuff"
        ).contains(term);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}