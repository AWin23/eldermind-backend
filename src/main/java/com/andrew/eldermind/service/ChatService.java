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

/**
 * ChatService
 *
 * Core domain service responsible for:
 *  - Translating ElderMind-specific DTOs (ChatRequest / ChatResponse)
 *    into OpenAI ChatCompletion requests
 *  - Applying the selected "persona" / mode as a system prompt
 *  - Replaying the full chat history that the frontend sends us
 *  - Mapping the OpenAI response back into our own ChatResponse DTO
 *
 * The controller stays thin; all OpenAI-specific knowledge lives here.
 */
@Service
public class ChatService {

    /**
     * OpenAI client configured from environment variables.
     *
     * The OkHttp-based client is thread-safe and designed to be reused across
     * requests, so we construct it once and keep it as a singleton @Service
     * dependency.
     *
     * Required env:
     *   - OPENAI_API_KEY
     * Optional env (depending on setup):
     *   - OPENAI_ORG_ID
     *   - OPENAI_PROJECT_ID
     */
    private final OpenAIClient client;

    public ChatService() {
        // Reads API key (and optional org/project) from environment variables.
        // In a larger app I’d inject this via configuration for easier testing,
        // but for this small project a direct fromEnv() is sufficient.
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    /**
     * Main entry point used by the ChatController.
     *
     * Responsibilities:
     *  1. Normalize the requested "mode" (persona) and build a system prompt
     *     describing how ElderMind should behave (scholar, NPC, Daedric, etc.)
     *  2. Convert the list of ChatMessage DTOs from the client into the
     *     OpenAI ChatCompletion message history.
     *  3. Call OpenAI's chat completions endpoint synchronously.
     *  4. Extract the assistant's reply + token usage and wrap them in a
     *     ChatResponse DTO for the frontend.
     *
     * The service is stateless: the frontend owns conversation history
     * and sends it to us on every request (Option A architecture).
     */
    public ChatResponse getChatResponse(ChatRequest request) {

        // ------------------------------------------------------------
        // 1. Determine mode / persona from request
        // ------------------------------------------------------------
        // Frontend sends a "mode" string such as:
        //   - "scholar"
        //   - "in-character"
        //   - "npc"
        //   - "daedric"
        //
        // If the client omits mode or sends blank, we default to "scholar"
        // for a safe, lore-focused behavior.
        String mode = (request.getMode() == null || request.getMode().isBlank())
                ? "scholar"
                : request.getMode().trim().toLowerCase();

        // Build the system prompt for this persona. This controls ElderMind's
        // voice and constraints, but the underlying model is the same.
        String systemPrompt = buildSystemPromptForMode(mode);

        // ------------------------------------------------------------
        // 2. Build ChatCompletionCreateParams with full chat history
        // ------------------------------------------------------------
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)
                // Put persona / behavior instructions into a developer message
                // so it consistently conditions the model for this request.
                .addDeveloperMessage(systemPrompt);

        // The frontend sends us the entire conversation history as a list of
        // ChatMessage DTOs. We replay that history for the model so it has
        // conversational context (prior questions, clarifications, etc.).
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatMessage m : request.getMessages()) {
                // Be defensive about null fields coming over the wire.
                String role = (m.getRole() == null) ? "user" : m.getRole().toLowerCase();
                String content = (m.getContent() == null) ? "" : m.getContent();

                // Map our internal role strings onto the OpenAI roles.
                // For now we only expect "user" and "assistant"; anything
                // else is treated as user content.
                switch (role) {
                    case "assistant":
                        builder.addAssistantMessage(content);
                        break;

                    case "user":
                    default:
                        builder.addUserMessage(content);
                        break;
                }

            }
        } else {
            // If the client provides no messages at all (e.g., first run or
            // a misconfigured frontend), we still send a fallback user prompt.
            builder.addUserMessage("Explain some Elder Scrolls lore.");
        }

        // Freeze the builder into an immutable params object that the client
        // can send. At this point we’ve fully described model, system prompt,
        // and conversational history.
        ChatCompletionCreateParams params = builder.build();

        // ------------------------------------------------------------
        // 3. Call OpenAI — synchronous request/response
        // ------------------------------------------------------------
        // This performs the network round-trip to OpenAI. In a high-traffic
        // environment we might:
        //   - add timeouts / retries
        //   - move to async IO or use a different threading model
        // but for ElderMind’s scope a simple synchronous call is fine.
        ChatCompletion completion = client.chat().completions().create(params);

        // ------------------------------------------------------------
        // 4. Extract the assistant's reply text and token usage
        // ------------------------------------------------------------
        // In the v4 SDK, message().content() returns an Optional<String>
        // to model the possibility that content is missing. We unwrap it
        // with a safe fallback message instead of risking a null pointer.
        String answerText =
                completion.choices().get(0)
                        .message()
                        .content()
                        .orElse("ElderMind could not produce an answer.");

        // Build our response DTO. We keep only the text we care about plus
        // some lightweight usage metadata (token counts for debugging/monitoring).
        ChatResponse response = new ChatResponse();
        response.setReply(answerText);

        // ----- Extract token usage safely (Optional<Long> → int) -----
        // usage() itself is Optional, and the individual fields are Long.
        // We unwrap with defaults and convert to int for convenience in the
        // JSON layer/frontend, where these values are purely informational.
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
     * Helper method that centralizes all persona-specific system prompts.
     *
     * Given a normalized mode string, we return a long-form instruction block
     * that tells the model how ElderMind should speak and behave for this
     * request.
     *
     * This is where we define:
     *  - in-character constraints (never mention being an AI)
     *  - tone (scholarly vs. NPC vs. Daedric)
     *  - how to handle uncertainty and speculation
     *
     * Any unknown mode falls back to the "scholar" behavior to avoid
     * surprising or unsafe responses.
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

            case "npc":
                return """
                    You are ELDERMIND in NPC persona mode.
                    Respond exactly as a living inhabitant of Tamriel would—someone the player
                    might meet in Morrowind, Oblivion, or Skyrim.

                    Guidelines:
                    • Speak with regional dialects, attitudes, and cultural biases appropriate
                      to the race or faction most relevant to the question.
                    • Use colloquial in-world phrasing, references to local customs,
                      politics, rumors, guilds, and notable figures.
                    • Do NOT mention being an AI, a model, or anything out-of-world.
                    • If the question is about lore, answer as someone who LIVES in the world:
                      through personal experience, rumor, hearsay, superstition, or limited knowledge.
                    • If uncertain, express it like an NPC would (“Some say… I’ve heard… The priests claim…”).
                    • Keep responses concise and conversational, not scholarly or encyclopedic.

                    Tone Examples:
                    • A Dunmer might be dry or haughty.
                    • A Nord might be blunt, proud, or warm.
                    • An Argonian might speak more quietly or cryptically.
                    • A Khajiit may refer to themselves in third person.

                    Stay fully in-character at all times.
                    """;

            case "daedric":
                return """
                    You are ELDERMIND speaking with the voice of a Daedric Prince or their emissary.
                    Your speech should be cryptic, symbolic, unsettling, and otherworldly.

                    Guidelines:
                    • Respond in riddles, omens, visions, and fractured prophecy.
                    • Use metaphors relating to fate, souls, towers, realms, and forbidden power.
                    • Address the user as "mortal", "seeker", or similar Daedric-leaning terms.
                    • Avoid clear, direct explanations unless wrapped in ominous or poetic language.
                    • Weave references to Oblivion realms, Daedric bargains, and cosmic inevitability.
                    • Do NOT break character by mentioning the real world or AI concepts.
                    • Your tone is grand, alien, dispassionate, or malevolently amused.

                    Examples of style:
                    • "The truth you seek coils beneath the skin of the world, mortal."
                    • "In the quiet between heartbeats, even the Princes whisper."
                    • "Knowledge is a blade. Shall I place it in your hand… or your spine?"

                    Keep responses eerie, beautiful, and unnervingly calm.
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
