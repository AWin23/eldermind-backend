package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.dto.ChatMessage;
import org.springframework.stereotype.Service;

// ✅ OpenAI Java SDK imports (v4.9.0)
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

@Service
public class ChatService {

    /**
     * OpenAI client configured from environment variables.
     *
     * Requires:
     *   export OPENAI_API_KEY="sk-xxxx"
     *
     * You can also set OPENAI_ORG_ID and OPENAI_PROJECT_ID if you use them,
     * but only OPENAI_API_KEY is required for basic usage.
     */
    private final OpenAIClient client;

    public ChatService() {
        // This reads OPENAI_API_KEY (and friends) from env or system props
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    /**
     * Main entry point called by your ChatController.
     * Takes the incoming ChatRequest, calls the OpenAI Chat Completions API,
     * and returns a ChatResponse DTO for the frontend.
     */
    public ChatResponse getChatResponse(ChatRequest request) {

        // ------------------------------------------------------------
        // 1. Extract the latest user message from your ChatRequest DTO
        // ------------------------------------------------------------
        String userContent = "Explain some Elder Scrolls lore.";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last.getContent() != null && !last.getContent().isBlank()) {
                userContent = last.getContent();
            }
        }

        

        // ------------------------------------------------------------
        // 2. Build ChatCompletionCreateParams for the Chat Completions API
        // ------------------------------------------------------------
        // For now we send a single user message.
        // Later we can add system prompts + full conversation history.
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(userContent)
                // You can upgrade to GPT_4_1 later if you want more power
                .model(ChatModel.GPT_4_1_MINI)
                .build();

        // ------------------------------------------------------------
        // 3. Call OpenAI: this actually hits the API
        // ------------------------------------------------------------
        ChatCompletion completion = client.chat().completions().create(params);

        // ------------------------------------------------------------
        // 4. Extract something usable for now
        // ------------------------------------------------------------
        String answerText =
        completion.choices().get(0)
            .message()
            .content()
            .orElse("ElderMind could not produce an answer.");



        // ------------------------------------------------------------
        // Map result into your ChatResponse DTO
        // ------------------------------------------------------------
        ChatResponse response = new ChatResponse();
        response.setReply(answerText);

        // ----- Extract token usage safely -----
        response.setPromptTokens(
        completion.usage().map(u -> u.promptTokens()).orElse(0L).intValue()
        );

        response.setCompletionTokens(
                completion.usage().map(u -> u.completionTokens()).orElse(0L).intValue()
        );

        return response;
    }
}
