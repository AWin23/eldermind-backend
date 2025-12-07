package com.andrew.eldermind.service;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.dto.ChatMessage;
import org.springframework.stereotype.Service;

// OpenAI Java SDK imports (v4.x)
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
     */
    private final OpenAIClient client;

    public ChatService() {
        // Reads OPENAI_API_KEY (and optional org/project) from env
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    /**
     * Main entry point called by ChatController.
     * - Reads the user's last message from ChatRequest
     * - Builds a system/developer prompt + user message
     * - Calls OpenAI Chat Completions API
     * - Returns a ChatResponse DTO to the frontend
     */
    public ChatResponse getChatResponse(ChatRequest request) {

        // ------------------------------------------------------------
        // 1. Extract the latest user message from your ChatRequest DTO
        // ------------------------------------------------------------
        String userContent = "Explain some Elder Scrolls lore.";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last.getContent() != null && !last.getContent().isBlank()) {
                userContent = last.getContent();  // <-- what the user actually typed
            }
        }

        // ------------------------------------------------------------
        // 2. Build ChatCompletionCreateParams for the Chat Completions API
        //    Here is where we add the *system-style* prompt.
        // ------------------------------------------------------------
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)

                // 👇 SYSTEM / DEVELOPER PROMPT: ElderMind persona
                .addDeveloperMessage("""
                    You are ELDERMIND — an Elder Scrolls lore expert.
                    You speak with an in-universe, scholarly tone and reference
                    canon sources from TES3, TES4, TES5, ESO, and official writings.
                    If something is speculative or fan theory, clearly label it as such.
                    """)
                // 👇 USER MESSAGE: what came from the frontend
                .addUserMessage(userContent)

                .build();

        // ------------------------------------------------------------
        // 3. Call OpenAI: this actually hits the API
        // ------------------------------------------------------------
        ChatCompletion completion = client.chat().completions().create(params);

        // ------------------------------------------------------------
        // 4. Extract just the assistant's text reply
        //    `message().content()` returns Optional<String>, so we unwrap safely.
        // ------------------------------------------------------------
        String answerText =
                completion.choices().get(0)
                        .message()
                        .content()
                        .orElse("ElderMind could not produce an answer.");

        // ------------------------------------------------------------
        // 5. Map result into your ChatResponse DTO
        // ------------------------------------------------------------
        ChatResponse response = new ChatResponse();
        response.setReply(answerText);

        // ----- Extract token usage safely (Optional<Long> → int) -----
        int promptTokens = completion.usage()
                .map(u -> u.promptTokens())
                .orElse(0L)
                .intValue();

        int completionTokens = completion.usage()
                .map(u -> u.completionTokens())
                .orElse(0L)
                .intValue();

        response.setPromptTokens(promptTokens);
        response.setCompletionTokens(completionTokens);

        return response;
    }
}
