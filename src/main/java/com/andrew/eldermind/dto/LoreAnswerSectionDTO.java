package com.andrew.eldermind.dto;

public class LoreAnswerSectionDTO {
    private String label;       // "Canon" | "In-universe belief" | "Scholarly debate"
    private String text;        // section content
    
    // Getters and Setters for LoreAnswerSectionDTO
    public String getLabel() {
        return label;
    }   

     void setLabel(String label) {
        this.label = label;
    }

    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
}
