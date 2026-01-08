package com.andrew.eldermind.lore.corpus;

public class LoreMatch {

    private final LoreDocument document;
    private final double score;

    public LoreMatch(LoreDocument document, double score) {
        this.document = document;
        this.score = score;
    }

    public LoreDocument getDocument() {
        return document;
    }

    public double getScore() {
        return score;
    }
}
