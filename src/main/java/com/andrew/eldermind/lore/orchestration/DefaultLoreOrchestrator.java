package com.andrew.eldermind.lore.orchestration;

import com.andrew.eldermind.dto.ChatRequest;
import com.andrew.eldermind.dto.ChatResponse;
import com.andrew.eldermind.dto.DualRetrievalResult;
import com.andrew.eldermind.dto.RetrievalDecision;
import com.andrew.eldermind.dto.FallbackReason;


import com.andrew.eldermind.service.ChatService;
import com.andrew.eldermind.lore.retrieval.DualLoreRetriever;
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
    private static final String RETRIEVER_VERSION = "keyword-v1";

    private static final int TOP_K = 4; // Small K to keep token usage low and results curated


    private final DualLoreRetriever dualLoreRetriever;
    private final LorePromptAssembler lorePromptAssembler;
    private final LoreLLMGateway loreLLMGateway;
    private final ChatService chatService;
    private final QueryAnalyzer queryAnalyzer;
    private final HybridRetriever hybridRetriever;

    /**
     * DefaultLoreOrchestrator
     *
     * High-level pipeline for ElderMind's grounded responses.
     *
     * We intentionally split responsibilities into focused components:
     *
     * 1) DualLoreRetriever (Retrieval + Evaluation)
     *    - Runs BOTH retrieval strategies (Keyword + Embeddings)
     *    - Chooses the best candidate set using simple confidence thresholds
     *    - Produces a RetrievalDecision for observability (why retrieval was used/skipped)
     *
     * 2) LorePromptAssembler (Context Packaging)
     *    - Converts top LoreMatch results into a compact “evidence pack”
     *    - Formats citations / section headers to keep tokens low and context readable
     *
     * 3) LoreLLMGateway (LLM Boundary)
     *    - Encapsulates the call to OpenAI / ChatService
     *    - Keeps orchestration logic testable and decoupled from the vendor API
     *
     * Orchestration flow (answer()):
     *  - Extract latest user query
     *  - Run retrieval (keyword + embeddings) and get a decision + matches
     *  - Apply gating (thresholds) to avoid injecting weak/irrelevant lore
     *  - Assemble the final prompt (chat history + persona + evidence)
     *  - Call the LLM and return ChatResponse (optionally includes sources)
     */
    public DefaultLoreOrchestrator(
            DualLoreRetriever dualLoreRetriever,
            LorePromptAssembler lorePromptAssembler,
            LoreLLMGateway loreLLMGateway,
            ChatService chatService,
            QueryAnalyzer queryAnalyzer,
            HybridRetriever hybridRetriever
    ) {
        this.dualLoreRetriever = dualLoreRetriever;
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
         * It allows us to understand:
         *  - whether retrieval was attempted,
         *  - whether it was used,
         *  - why we fell back when it was not used.
         *
         * This is critical for observability, debugging, and AI safety.
         */
        RetrievalDecision decision = new RetrievalDecision();

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
         * Step 1: Attempt lore retrieval.
         * Any exception here should fail safely and fall back to chat-only mode.
         * From this point onward, retrieval has been attempted.
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

        // Populate decision metadata manually (Phase 2 simplified model)
        decision.setMatchedDocs(matches.size());
        decision.setTopScore(
                matches.isEmpty() ? 0.0 : matches.get(0).getScore()
        );

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
         * Step 2.5: Hard-evidence gate (anti-false-positive filter)
         *
         * Keyword scoring can be fooled by generic tokens like "house" or "happened",
         * which appear across many lore documents. That can produce a non-zero score
         * even when none of the retrieved snippets actually mention the user's subject.
         *
         * To prevent injecting irrelevant lore, we require that at least one
         * retrieved snippet contains at least one "must-hit" term from the query.
         */
        var keywords = queryAnalyzer.extractKeywords(latestUserQuery);

        // Tokens that are too generic to count as proof of relevance.
        // (These often show up in questions and can inflate keyword scores.)
        var genericTerms = java.util.Set.of(
                "house", "war", "event", "history", "story", "thing", "stuff",
                "happened", "happen", "ended", "first"
        );

        // Must-hit terms = extracted keywords minus generic/noise terms.
        // For "What happened to House Hlaalu?" this becomes {"hlaalu"}.
        var mustHit = keywords.stream()
                .filter(token -> !genericTerms.contains(token))
                .collect(java.util.stream.Collectors.toSet());

        
        // If query is too generic, do NOT retrieve (prevents Warp-in-the-West or other irrelevant queries spam)
        if (mustHit.isEmpty()) {
            decision.setRetrievalUsed(false);
            decision.setFallbackReason(FallbackReason.NO_HARD_EVIDENCE); // or QUERY_TOO_GENERIC if you add it
            logDecision(decision, latestUserQuery);
            return chatService.getChatResponse(request);
        }

        // If we have must-hit terms, require that at least one retrieved doc
        // actually contains one of them in its title or body.
        boolean hasHardEvidence =
                mustHit.isEmpty() || matches.stream().anyMatch(m -> {
                    String t = safe(m.getDocument().getTitle()).toLowerCase();
                    String b = safe(m.getDocument().getText()).toLowerCase();
                    return mustHit.stream().anyMatch(term -> t.contains(term) || b.contains(term));
                });

        // If no hard evidence of relevance, skip retrieval to avoid poisoning the response.
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

