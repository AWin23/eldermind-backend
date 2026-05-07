package com.andrew.eldermind.dto;

import java.util.List;

// This class encapsulates the results of validating the model's output answer
public class OutputValidationResult {

    boolean passed; // overall pass/fail result
    List<String> warnings; // validation warnings

    boolean alignmentPassed; // whether the answer aligns with retrieved lore
    boolean confidencePassed; // whether confidence checks passed
    boolean fallbackPassed; // whether fallback handling passed

    /**
     * Empty constructor.
     * Useful for frameworks / serialization.
     */
    public OutputValidationResult() {
    }

    /**
     * Main constructor used by OutputValidatorService.
     */
    public OutputValidationResult(
            boolean passed,
            List<String> warnings,
            boolean alignmentPassed,
            boolean confidencePassed,
            boolean fallbackPassed
    ) {
        this.passed = passed;
        this.warnings = warnings;
        this.alignmentPassed = alignmentPassed;
        this.confidencePassed = confidencePassed;
        this.fallbackPassed = fallbackPassed;
    }

    /* Getters and Setters */

    // Getters
    public boolean isPassed() {
        return passed;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isAlignmentPassed() {
        return alignmentPassed;
    }

    public boolean isConfidencePassed() {
        return confidencePassed;
    }

    public boolean isFallbackPassed() {
        return fallbackPassed;
    }

    // Setters
    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public void setAlignmentPassed(boolean alignmentPassed) {
        this.alignmentPassed = alignmentPassed;
    }

    public void setConfidencePassed(boolean confidencePassed) {
        this.confidencePassed = confidencePassed;
    }

    public void setFallbackPassed(boolean fallbackPassed) {
        this.fallbackPassed = fallbackPassed;
    }
}