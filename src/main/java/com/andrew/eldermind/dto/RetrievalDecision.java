package com.andrew.eldermind.dto;

public class RetrievalDecision {

    private boolean retrievalAttempted;   // Was retrieval logic executed
    private boolean retrievalUsed;         // Was evidence injected into the prompt

    private int matchedDocs;               // Number of documents with score > 0
    private double topScore;               // Highest relevance score
    private Double threshold;              // Gating threshold (nullable)

    private FallbackReason fallbackReason; // Why retrieval was skipped (nullable)
    private String retrieverVersion;       // e.g. "keyword-v1"

    // Constructors, getters, setters (or Lombok if you use it)
    public RetrievalDecision() {
        // Default constructor
    }

    public RetrievalDecision(boolean retrievalAttempted, boolean retrievalUsed, int matchedDocs, double topScore, Double threshold, FallbackReason fallbackReason, String retrieverVersion) {
        this.retrievalAttempted = retrievalAttempted;
        this.retrievalUsed = retrievalUsed;
        this.matchedDocs = matchedDocs;
        this.topScore = topScore;
        this.threshold = threshold;
        this.fallbackReason = fallbackReason;
        this.retrieverVersion = retrieverVersion;
    }

    // Getters and setters
    public boolean isRetrievalAttempted() {
        return retrievalAttempted;  
    }

    public void setRetrievalAttempted(boolean retrievalAttempted) {
        this.retrievalAttempted = retrievalAttempted;
    }

    public boolean isRetrievalUsed() {
        return retrievalUsed;
    }

    public void setRetrievalUsed(boolean retrievalUsed) {
        this.retrievalUsed = retrievalUsed;
    }

    public int getMatchedDocs() {
        return matchedDocs;
    }

    public void setMatchedDocs(int matchedDocs) {
        this.matchedDocs = matchedDocs;
    }

    public double getTopScore() {
        return topScore;
    }

    public void setTopScore(double topScore) {
        this.topScore = topScore;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public FallbackReason getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(FallbackReason fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getRetrieverVersion() {
        return retrieverVersion;
    }

    public void setRetrieverVersion(String retrieverVersion) {
        this.retrieverVersion = retrieverVersion;
    }
}
