package com.andrew.eldermind.lore.corpus;

import java.util.List;


public class LoreDocument {
    private String id;
    private String source;      // UESP / InGameBook
    private String title;
    private String url;
    private String text;        // full snippet text
    private List<Double> embedding; // precomputed vector

    public LoreDocument() {}

    // getters/setters
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "LoreDocument{" + "id='" + id + '\'' +
                ", source='" + source + '\'' + ", title='" + title + '\'' + ", url='" + url + '\'' +
                ", text='" + text + '\'' + ", embedding=" + embedding + '}';    
    }

}
