package com.andrew.eldermind.lore.orchestration;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.dto.FallbackReason;


import com.andrew.eldermind.service.ChatService;
import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.corpus.LoreMatch;
import com.andrew.eldermind.lore.retrieval.HybridRetriever;
import com.andrew.eldermind.lore.retrieval.QueryAnalyzer;
import com.andrew.eldermind.lore.gateway.LoreLLMGateway;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final int TOP_K = 4; // Small K to keep token usage low and results curated

    private final LorePromptAssembler lorePromptAssembler;
    private final LoreLLMGateway loreLLMGateway;
    private final ChatService chatService;
    private final QueryAnalyzer queryAnalyzer;
    private final HybridRetriever hybridRetriever;


    /**
     * DefaultLoreOrchestrator
     *
     * High-level pipeline for ElderMind's grounded responses (Phase 2: Hybrid Retrieval).
     *
     * Responsibilities are intentionally separated into focused components:
     *
     * 1) HybridRetriever (Retrieval + Ranking)
     *    - Runs BOTH retrieval signals (Keyword + Embeddings)
     *    - Merges scores per document using weighted hybrid scoring
     *    - Normalizes keyword scores to align with cosine similarity
     *    - Returns top K ranked LoreMatch results
     *
     * 2) Hard-Evidence Gate (Safety Layer)
     *    - Extracts meaningful query terms ("mustHit")
     *    - Requires at least one retrieved document to contain at least one mustHit term
     *    - Prevents generic or weak queries from injecting irrelevant lore
     *
     * 3) LorePromptAssembler (Context Packaging)
     *    - Converts top LoreMatch results into a compact evidence pack
     *    - Formats citations / section headers while minimizing token usage
     *
     * 4) LoreLLMGateway (LLM Boundary)
     *    - Encapsulates the call to OpenAI / ChatService
     *    - Keeps orchestration logic testable and vendor-agnostic
     *
     * Orchestration flow (answer()):
     *  - Extract latest user query
     *  - Run hybrid retrieval to obtain ranked candidates
     *  - Apply hard-evidence gate to ensure lexical grounding
     *  - If gating passes → assemble evidence + persona + history
     *  - If gating fails → fallback to chat-only mode
     *  - Call LLM and return ChatResponse (optionally includes sources)
     *
     * Architectural Notes:
     *  - Retrieval determines relevance.
     *  - The gate determines safety.
     *  - The orchestrator determines whether grounding is used.
     */

    public DefaultLoreOrchestrator(
            LorePromptAssembler lorePromptAssembler,
            LoreLLMGateway loreLLMGateway,
            ChatService chatService,
            QueryAnalyzer queryAnalyzer,
            HybridRetriever hybridRetriever
    ) {
        this.lorePromptAssembler = lorePromptAssembler;
        this.loreLLMGateway = loreLLMGateway;
        this.chatService = chatService;
        this.queryAnalyzer = queryAnalyzer;
        this.hybridRetriever = hybridRetriever;
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
         */
        RetrievalDecision decision = new RetrievalDecision();

        /**
         * Step 0: Extract the most recent user-authored message.
         */
        String latestUserQuery = extractLatestUserMessage(request);

        /**
         * Step 0a: If the query is empty, skip retrieval entirely.
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
         * Step 0b: Extract keywords and compute must-hit terms BEFORE retrieval.
         * If the query is too generic, skip retrieval entirely.
         */
        var keywords = queryAnalyzer.extractKeywords(latestUserQuery);

        var genericTerms = java.util.Set.of(
                // Question words
                "what", "which", "who", "where", "when", "why", "how",

                // Weak generic nouns
                "house", "war", "event", "history", "story", "thing", "stuff", "great",

                // Weak verbs
                "happened", "happen", "ended", "lost",

                // Filler / emphasis words
                "too", "very", "really", "close",

                // Misc common noise
                "about", "tell", "explain"
        );

        var mustHit = keywords.stream()
                .map(String::toLowerCase)
                .filter(token -> token.length() >= 3)
                .filter(token -> !genericTerms.contains(token))
                .collect(java.util.stream.Collectors.toSet());

        System.out.println("[Gate] query=\"" + latestUserQuery + "\" mustHit=" + mustHit);

        if (mustHit.isEmpty()) {
            decision.setRetrievalAttempted(false);   // retrieval never ran
            decision.setRetrievalUsed(false);
            decision.setMatchedDocs(0);
            decision.setTopScore(0.0);
            decision.setFallbackReason(FallbackReason.NO_HARD_EVIDENCE);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        /**
         * Step 1: Attempt lore retrieval.
         * From this point onward, retrieval has actually been attempted.
         */
        decision.setRetrievalAttempted(true);

        List<LoreMatch> matches;

        try {
            matches = hybridRetriever.retrieveTopK(latestUserQuery, TOP_K);
        } catch (Exception e) {
            decision.setRetrievalUsed(false);
            decision.setMatchedDocs(0);
            decision.setTopScore(0.0);
            decision.setFallbackReason(FallbackReason.ERROR);

            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        decision.setMatchedDocs(matches.size());
        decision.setTopScore(matches.isEmpty() ? 0.0 : matches.get(0).getScore());

        /**
         * Step 2: If no documents matched at all, fall back.
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
         * Step 2.5: Hard-evidence gate.
         * At least one retrieved doc must contain at least one must-hit term.
         */
        boolean hasHardEvidence = matches.stream().anyMatch(m -> {
            String t = safe(m.getDocument().getTitle()).toLowerCase();
            String b = safe(m.getDocument().getText()).toLowerCase();
            return mustHit.stream().anyMatch(term -> t.contains(term) || b.contains(term));
        });

        if (!hasHardEvidence) {
            decision.setRetrievalUsed(false);
            decision.setFallbackReason(FallbackReason.NO_HARD_EVIDENCE);

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

    // Utility to safely handle null strings when checking for keywords in evidence.
    private String safe(String s) {
    return (s == null) ? "" : s;
}
}

