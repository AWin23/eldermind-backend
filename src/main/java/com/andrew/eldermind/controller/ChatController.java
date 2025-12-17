package com.andrew.eldermind.controller;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.lore.orchestration.LoreOrchestrator;
import com.andrew.eldermind.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final LoreOrchestrator loreOrchestrator;
    private final ChatService chatService;

    public ChatController(LoreOrchestrator loreOrchestrator, ChatService chatService) {
        this.loreOrchestrator = loreOrchestrator;
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {

        // Default to scholar mode when missing
        String mode = (request.getMode() == null || request.getMode().isBlank())
                ? "scholar"
                : request.getMode().trim().toLowerCase();

        // Sprint 2: use lore intelligence for scholar mode
        if ("scholar".equals(mode)) {
            boolean includeSources = false; // later: read from query param or request field
            return loreOrchestrator.answer(request, includeSources);
        }

        // Other modes (npc/daedric/etc) can stay chat-only for now
        return chatService.getChatResponse(request);
    }
}
