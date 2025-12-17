package com.andrew.eldermind.lore.orchestration;

import org.springframework.stereotype.Component;
import com.andrew.eldermind.lore.corpus.LoreDocument;

import java.util.List;

/**
 * Formats retrieved lore snippets into a stable "evidence block" string
 * that we inject into the LLM prompt.
 *
 * Why this exists:
 * - Keeps orchestration logic clean (no giant StringBuilder in services)
 * - Makes prompt formatting consistent and easy to tweak
 */
@Component
public class LorePromptAssembler {

    /**
     * Builds the evidence block that will be included in the prompt.
     * The model should base its answer primarily on these snippets.
     */
    public String buildEvidenceBlock(List<LoreDocument> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "LORE EVIDENCE:\n(No relevant sources retrieved.)\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("LORE EVIDENCE (use this as grounding):\n");

        // Each snippet is "atomic" knowledge: one claim/perspective per entry.
        for (LoreDocument doc : evidence) {
            sb.append("\n---\n");
            sb.append("Snippet ID: ").append(safe(doc.getId())).append("\n");
            sb.append("Source: ").append(safe(doc.getSource())).append("\n");
            sb.append("Title: ").append(safe(doc.getTitle())).append("\n");
            sb.append("Excerpt: ").append(safe(doc.getText())).append("\n");
        }
        sb.append("\n---\n");

        // Stronger grounding rules to reduce hallucinations and keep answers “evidence-first”.
        sb.append("INSTRUCTIONS (follow strictly):\n");
        sb.append("1) Use ONLY the evidence snippets above for factual claims.\n");
        sb.append("2) If you must add outside Elder Scrolls knowledge, label it clearly as:\n");
        sb.append("   \"General lore knowledge (not from provided sources): ...\"\n");
        sb.append("3) If the evidence is uncertain, incomplete, or conflicting, say so explicitly.\n");
        sb.append("4) Do NOT invent dates, names, titles, or causes not present in evidence.\n");
        sb.append("5) Prefer quoting or paraphrasing the excerpts rather than expanding beyond them.\n");

        // Optional but very effective: ask the model to cite snippet IDs inline.
        // This makes answers feel “engine-like” and forces it to stay grounded.
        sb.append("\nRESPONSE FORMAT (use Markdown headers):\n");
        sb.append("## Answer (grounded)\n");
        sb.append("## What the evidence supports\n");
        sb.append("## What is disputed or unknown\n");
        sb.append("## General lore knowledge (only if used, and clearly labeled)\n");


        return sb.toString();
    }

    /**
     * Builds a simple "Sources" footer for when the UI toggle is ON.
     * This is a Sprint 2 MVP approach (later you can return structured sources in JSON).
     */
    public String buildSourcesFooter(List<LoreDocument> evidence) {
        if (evidence == null || evidence.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("Sources:\n");
        for (LoreDocument doc : evidence) {
            sb.append("- ")
              .append(safe(doc.getTitle()))
              .append(" (")
              .append(safe(doc.getSource()))
              .append(")");
            if (doc.getUrl() != null && !doc.getUrl().isBlank()) {
                sb.append(" — ").append(doc.getUrl());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}
