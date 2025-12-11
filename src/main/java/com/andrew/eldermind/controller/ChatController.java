// controller/ChatController.java
package com.andrew.eldermind.controller;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.service.ChatService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173") // <-- allow your Vite frontend
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.getChatResponse(request);
    }
}
