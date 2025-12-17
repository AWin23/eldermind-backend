package com.andrew.eldermind.lore.gateway;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.service.ChatService;
import org.springframework.stereotype.Service;

/**
 * OpenAIChatLoreGateway
 *
 * Thin adapter around ChatService.
 *
 * Responsibility:
 * - Pass the evidenceBlock into ChatService so it can be appended to the
 *   developer message (system prompt) for grounding.
 *
 * Note:
 * - ChatRequest does NOT store systemPrompt in your architecture.
 * - The backend builds the persona prompt from request.getMode().
 */
@Service
public class OpenAIChatLoreGateway implements LoreLLMGateway {

    private final ChatService chatService;

    public OpenAIChatLoreGateway(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public ChatResponse generateLoreAnswer(ChatRequest request, String evidenceBlock) {
        // Delegate to ChatService overload that supports extra developer context.
        return chatService.getChatResponse(request, evidenceBlock);
    }
}
