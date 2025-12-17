// dto/ChatResponse.java
package com.andrew.eldermind.dto;

import java.util.List;

public class ChatResponse {
    private String reply; // text reply from the model
    private int promptTokens; // number of tokens in the prompt
    private int completionTokens; // number of tokens in the completion

    // Structured variables for LoreChat Response
    private List<LoreAnswerSectionDTO> sections;     // structured sections
    private List<LoreSnippetDTO> sources; 

    // getters + setters
    public String getReply() {
        return reply;
    }
    
    public void setReply(String reply) {
        this.reply = reply;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public List<LoreAnswerSectionDTO> getSections() {
        return sections;
    }

    public void setSections(List<LoreAnswerSectionDTO> sections) {
        this.sections = sections;
    }

    public List<LoreSnippetDTO> getSources() {
        return sources;
    }

    public void setSources(List<LoreSnippetDTO> sources) {
        this.sources = sources;
    }
}
