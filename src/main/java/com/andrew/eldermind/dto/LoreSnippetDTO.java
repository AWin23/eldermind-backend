package com.andrew.eldermind.dto;

public class LoreSnippetDTO {
    private String id;          // e.g. "uesp-azura-001"
    private String source;      // "UESP" | "InGameBook"
    private String title;       // page/book title
    private String excerpt;     // text injected to model
    private String url;         // optional
    private double score;       // retrieval score

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}