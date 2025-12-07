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
     * - Chooses a system prompt based on `mode`
     * - Calls OpenAI Chat Completions API
     * - Returns a ChatResponse DTO to the frontend
     */
    public ChatResponse getChatResponse(ChatRequest request) {

        // ------------------------------------------------------------
        // 1. Determine mode (behavior style) from request
        // ------------------------------------------------------------
        // If mode is null/blank, we default to "scholar".
        String mode = (request.getMode() == null || request.getMode().isBlank())
                ? "scholar"
                : request.getMode().trim().toLowerCase();

        // We'll pick the system prompt text based on `mode`.
        String systemPrompt = buildSystemPromptForMode(mode);

        // ------------------------------------------------------------
        // 2. Extract the latest user message from your ChatRequest DTO
        // ------------------------------------------------------------
        String userContent = "Explain some Elder Scrolls lore.";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last.getContent() != null && !last.getContent().isBlank()) {
                userContent = last.getContent();  // <-- what the user actually typed
            }
        }

        // ------------------------------------------------------------
        // 3. Build ChatCompletionCreateParams for the Chat Completions API
        //    Here is where we add the mode-specific system prompt.
        // ------------------------------------------------------------
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)

                // SYSTEM / DEVELOPER PROMPT: ElderMind persona, varies by mode
                .addDeveloperMessage(systemPrompt)

                // USER MESSAGE: what came from the frontend
                .addUserMessage(userContent)

                .build();

        // ------------------------------------------------------------
        // 4. Call OpenAI: this actually hits the API
        // ------------------------------------------------------------
        ChatCompletion completion = client.chat().completions().create(params);

        // ------------------------------------------------------------
        // 5. Extract just the assistant's text reply
        //    `message().content()` returns Optional<String>, so we unwrap safely.
        // ------------------------------------------------------------
        String answerText =
                completion.choices().get(0)
                        .message()
                        .content()
                        .orElse("ElderMind could not produce an answer.");

        // ------------------------------------------------------------
        // 6. Map result into your ChatResponse DTO
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

    /**
     * Helper method:
     * Return a system prompt string based on the requested mode.
     * Right now we support:
     *   - "scholar"      → in-lore, canonical explanations
     *   - "in-character" → roleplay-style responses (stubbed, can refine later)
     * Any unknown mode falls back to "scholar".
     */
    private String buildSystemPromptForMode(String mode) {
        switch (mode) {
            case "in-character":
                return """
                    You are ELDERMIND in an in-character roleplay mode.
                    Respond as if you are a living inhabitant of Tamriel:
                    speak in-universe, with flavor, personality, and references
                    to in-world locations, people, and events.
                    Do NOT break character by mentioning being an AI or model.
                    If the user asks for out-of-world metadata (like release dates),
                    you may briefly drop character to answer, then return to roleplay.
                    """;

            case "scholar":
            default:
                return """
                    You are ELDERMIND — an Elder Scrolls lore scholar.
                    You explain lore with scholarly detail and clear structure,
                    referencing canon sources from TES3, TES4, TES5, ESO, and
                    official developer writings when relevant.
                    Maintain an in-universe, analytical tone.
                    If something is speculative or fan theory, clearly label it.
                    """;
        }
    }
}
