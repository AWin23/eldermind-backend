package com.andrew.eldermind.lore.orchestration;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.dto.FallbackReason;


import com.andrew.eldermind.service.ChatService;
import com.andrew.eldermind.lore.retrieval.LoreRetriever;
import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import com.andrew.eldermind.lore.gateway.LoreLLMGateway;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.andrew.eldermind.dto.RetrievalDecision;

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

    // Logger for logging purposes
    private static final Logger log = LoggerFactory.getLogger(DefaultLoreOrchestrator.class);

    // Constants for configuration and threshold constant
    private static final double DEFAULT_THRESHOLD = 0.15; // pick your gating value
    private static final String RETRIEVER_VERSION = "keyword-v1";


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

    /**
     * Logs retrieval decision metadata for observability.
     * This gives us an explainable trail of why retrieval was used or skipped.
     *
     * NOTE: We intentionally truncate the query to avoid noisy logs and accidental PII leakage.
     */
    private void logDecision(RetrievalDecision decision, String query) {
        if (decision == null) return;

        String safeQuery = (query == null) ? "" : query.trim();
        if (safeQuery.length() > 160) {
            safeQuery = safeQuery.substring(0, 160) + "...";
        }

        log.info(
            "RetrievalDecision attempted={} used={} matchedDocs={} topScore={} threshold={} fallbackReason={} retrieverVersion={} query=\"{}\"",
            decision.isRetrievalAttempted(),
            decision.isRetrievalUsed(),
            decision.getMatchedDocs(),
            decision.getTopScore(),
            decision.getThreshold(),
            decision.getFallbackReason(),
            decision.getRetrieverVersion(),
            safeQuery
        );
    }

    
    @Override
    public ChatResponse answer(ChatRequest request, boolean includeSources) {

        /**
         * RetrievalDecision captures explainability metadata for this request.
         * It allows us to understand:
         *  - whether retrieval was attempted,
         *  - whether it was used,
         *  - why we fell back when it was not used.
         *
         * This is critical for observability, debugging, and AI safety.
         */
        RetrievalDecision decision = new RetrievalDecision();
        decision.setRetrieverVersion(RETRIEVER_VERSION);
        decision.setThreshold(DEFAULT_THRESHOLD);

        /**
         * Step 0: Extract the most recent user-authored message.
         * We treat this as the canonical query for lore retrieval.
         */
        String latestUserQuery = extractLatestUserMessage(request);

        /**
         * Step 0a: If the query is empty or meaningless,
         * skip retrieval entirely and fall back to standard chat behavior.
         */
        if (latestUserQuery == null || latestUserQuery.trim().isEmpty()) {
            decision.setRetrievalAttempted(false);
            decision.setRetrievalUsed(false);
            decision.setMatchedDocs(0);
            decision.setTopScore(0.0);
            decision.setFallbackReason(FallbackReason.NO_KEYWORDS);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        /**
         * From this point onward, retrieval has been attempted.
         */
        decision.setRetrievalAttempted(true);

        int k = 4; // Small K to keep token usage low and results curated
        List<LoreMatch> matches;

        /**
         * Step 1: Attempt lore retrieval.
         * Any exception here should fail safely and fall back to chat-only mode.
         */
        try {
            matches = loreRetriever.retrieveTopK(latestUserQuery, k);
        } catch (Exception e) {
            decision.setRetrievalUsed(false);
            decision.setMatchedDocs(0);
            decision.setTopScore(0.0);
            decision.setFallbackReason(FallbackReason.ERROR);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        /**
         * Step 2: If no documents matched at all,
         * do not inject unrelated or low-quality evidence.
         */
        if (matches == null || matches.isEmpty()) {
            decision.setRetrievalUsed(false);
            decision.setMatchedDocs(0);
            decision.setTopScore(0.0);
            decision.setFallbackReason(FallbackReason.NO_MATCHES);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        /**
         * Step 3: Record relevance statistics.
         * Matches are assumed to be sorted by descending score.
         */
        double topScore = matches.get(0).getScore();
        decision.setTopScore(topScore);
        decision.setMatchedDocs(matches.size());

        /**
         * Step 4: Apply relevance gating.
         * If the best match is below threshold, we deliberately skip retrieval
         * to avoid poisoning the response with weak or unrelated lore.
         */
        if (topScore < DEFAULT_THRESHOLD) {
            decision.setRetrievalUsed(false);
            decision.setFallbackReason(FallbackReason.BELOW_THRESHOLD);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        /**
         * Step 5: Retrieval passed the gate.
         * Evidence will be injected into the prompt.
         */
        decision.setRetrievalUsed(true);
        decision.setFallbackReason(null);

        /**
         * Convert LoreMatch objects into raw LoreDocuments
         * for prompt assembly.
         */
        List<LoreDocument> evidence = matches.stream()
            .map(LoreMatch::getDocument)
            .toList();

        /**
         * Step 6: Build a grounded evidence block and generate the LLM response.
         * The LLM is required to use this evidence for factual claims.
         */
        String evidenceBlock = lorePromptAssembler.buildEvidenceBlock(evidence);
        ChatResponse response = loreLLMGateway.generateLoreAnswer(request, evidenceBlock);

        /**
         * Step 7 (Optional): Attach visible source citations for UI display.
         */
        if (includeSources) {
            response.setReply(
                response.getReply() + "\n\n" + lorePromptAssembler.buildSourcesFooter(evidence)
            );
        }

        /**
         * Final step: Log successful retrieval decision for observability.
         */
        logDecision(decision, latestUserQuery);

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

