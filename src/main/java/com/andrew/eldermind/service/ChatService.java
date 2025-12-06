// service/ChatService.java
package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    public ChatResponse getChatResponse(ChatRequest request) {
        ChatResponse response = new ChatResponse();
        response.setReply("ElderMind placeholder: the wheels of Tamriel are turning.");
        System.out.println("Calling the ChatService");
        response.setPromptTokens(0);
        response.setCompletionTokens(0);
        return response;
    }
}
