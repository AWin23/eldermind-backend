package com.andrew.eldermind.lore.orchestration;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.service.ChatService;
import com.andrew.eldermind.lore.retrieval.LoreRetriever;
import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.gateway.LoreLLMGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DefaultLoreOrchestrator:
 *
 * This is the coordinator for Sprint 2 lore intelligence.
 * It does NOT store chat history.
 * It does NOT do the OpenAI HTTP call itself (that stays in ChatService).
 *
 * It simply:
 *  - retrieves evidence snippets relevant to the latest user query,
 *  - builds a grounded context block,
 *  - and returns a prompt-ready result to the LLM layer.
 */
@Service
public class DefaultLoreOrchestrator implements LoreOrchestrator {

    private final LoreRetriever loreRetriever;
    private final LorePromptAssembler lorePromptAssembler;
    private final LoreLLMGateway loreLLMGateway;
    private final ChatService chatService;

    /**
     * We split responsibilities into:
     * - LoreRetriever: find relevant documents/snippets
     * - LorePromptAssembler: turn evidence into a clean prompt context
     * - LoreLLMGateway: the bridge to OpenAI (keeps orchestration testable)
     *
     * If you want fewer files, you *can* inline these into this class,
     * but splitting them makes it easier to reason about later.
     * 
    // 1) retrieve snippets
    // 2) build evidence pack
    // 3) label sections
    // 4) assemble prompt + call OpenAI chat
    // 5) return response with optional sources
    */
   public DefaultLoreOrchestrator(
       LoreRetriever loreRetriever,
       LorePromptAssembler lorePromptAssembler,
       LoreLLMGateway loreLLMGateway,
       ChatService chatService
    ) {
        this.loreRetriever = loreRetriever;
        this.lorePromptAssembler = lorePromptAssembler;
        this.loreLLMGateway = loreLLMGateway;
        this.chatService = chatService;
    }
    
    @Override
    public ChatResponse answer(ChatRequest request, boolean includeSources) {

        /**
         * Step 0: Determine the "latest user question".
         * In most chat systems, the last message in the list is the newest.
         * We assume frontend appends messages in order.
         */
        String latestUserQuery = extractLatestUserMessage(request);

        /**
         * Step 1: Retrieve the top K lore snippets for this user query.
         * Sprint 2: K is small (3–6) on purpose:
         *  - keeps token cost low
         *  - avoids overwhelming the model
         *  - makes the answer feel curated
         */
        int k = 4;
        List<LoreDocument> evidence = loreRetriever.retrieveTopK(latestUserQuery, k);
        if (evidence == null || evidence.isEmpty()) {
        // No relevant sources found → do normal ElderMind scholar response
        return chatService.getChatResponse(request);
}

        /**
         * Step 2: Convert those documents into an "evidence block"
         * that will be injected into the LLM prompt.
         *
         * This block is what actually makes responses grounded.
         * The LLM isn't asked to "remember Elder Scrolls lore"—
         * it is given source snippets to reference.
         */
        String evidenceBlock = lorePromptAssembler.buildEvidenceBlock(evidence);

        /**
         * Step 3: Call the LLM gateway (OpenAI layer).
         *
         * IMPORTANT:
         * The OpenAI API is stateless.
         * So on every request, we must send:
         *  - persona prompt (developer message)
         *  - conversation history (replayed messages)
         *  - evidence block (grounding)
         *  - latest user question
         *
         * The gateway handles the actual OpenAI SDK call.
         */
        ChatResponse response = loreLLMGateway.generateLoreAnswer(request, evidenceBlock);

        /**
         * Step 4 (Optional): Attach sources.
         * For now, your ChatResponse may not have a place for sources.
         * The typical next step is to upgrade ChatResponse or create LoreChatResponse.
         *
         * Sprint 2 MVP: we can also just append a "Sources:" section to the text.
         */
        if (includeSources && evidence != null && !evidence.isEmpty()) {
            response.setReply(
                response.getReply() + "\n\n" + lorePromptAssembler.buildSourcesFooter(evidence)
            );
        }

        return response;
    }

    /**
     * Pull the most recent user message from the request.
     * We keep this defensive since frontend might misbehave during development.
     */
    private String extractLatestUserMessage(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }

        // Walk backwards until we find a role == "user"
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            var msg = request.getMessages().get(i);
            String role = (msg.getRole() == null) ? "" : msg.getRole().toLowerCase();
            if ("user".equals(role)) {
                return msg.getContent() == null ? "" : msg.getContent();
            }
        }

        // Fallback: just take the last message’s content
        var last = request.getMessages().get(request.getMessages().size() - 1);
        return last.getContent() == null ? "" : last.getContent();
    }
}

