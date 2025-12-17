package com.andrew.eldermind.lore.gateway;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;

/**
 * Thin adapter that calls the existing LLM/OpenAI logic,
 * but allows LoreOrchestrator to pass extra grounding context (evidence).
 *
 * Why:
 * - Keeps orchestration testable
 * - Keeps OpenAI SDK details inside your existing service layer
 */
public interface LoreLLMGateway {
    ChatResponse generateLoreAnswer(ChatRequest request, String evidenceBlock);
}
