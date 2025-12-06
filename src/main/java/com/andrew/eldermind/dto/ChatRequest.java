// dto/ChatRequest.java
package com.andrew.eldermind.dto;

import java.util.List;

public class ChatRequest {
    private List<ChatMessage> messages;
    private String mode;

    // Getters + Setters
    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
