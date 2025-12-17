package com.andrew.eldermind.lore.orchestration;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;

/**
 * The LoreOrchestrator is the "brain" of Sprint 2.
 *
 * It coordinates the RAG-lite pipeline:
 *  1) Retrieve relevant lore snippets from the corpus
 *  2) Build an evidence block for grounding
 *  3) Hand off to ChatService / OpenAI prompt assembly
 *
 * This keeps your controller/service clean:
 * ChatService calls ONE method, and the orchestrator handles the lore steps.
 */
public interface LoreOrchestrator {

    /**
     * Creates a lore-grounded response using RAG-lite.
     *
     * @param request          chat request containing history + latest user question
     * @param includeSources   whether to include sources in response (for UI toggle)
     * @return a normal ChatResponse (you can later upgrade to LoreChatResponse)
     */
    ChatResponse answer(ChatRequest request, boolean includeSources);
}
