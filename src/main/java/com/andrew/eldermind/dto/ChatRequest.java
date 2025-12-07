package com.andrew.eldermind.dto;

import java.util.List;

public class ChatRequest {

    // Optional mode to control ElderMind's behavior.
    // Examples:
    //  - "scholar"      → in-lore, canonical explanations (default)
    //  - "in-character" → roleplay style (to add later)
    private String mode;

    // List of chat messages from the frontend (user, later assistant, etc.)
    private List<ChatMessage> messages;

    // ----- Getters and Setters -----

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
