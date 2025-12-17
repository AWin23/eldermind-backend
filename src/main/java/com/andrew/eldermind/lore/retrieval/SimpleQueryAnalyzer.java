package com.andrew.eldermind.lore.retrieval;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extremely lightweight "query understanding" step for Sprint 2.
 *
 * Purpose:
 * - Extract a set of important tokens from the user’s question
 *   (names, places, concepts) so retrieval can:
 *      1) boost exact lore term matches, and
 *      2) avoid relying on embeddings initially.
 *
 * Why this exists:
 * - Lore has many proper nouns ("Numidium", "Nerevar", "Red Mountain")
 *   and exact string matches are valuable signals.
 * - This is NOT a full NLP pipeline (no NER model).
 * - We intentionally keep it simple for RAG-lite.
 */
@Component
public class SimpleQueryAnalyzer implements QueryAnalyzer {

    /**
     * A small stopword list to remove “filler words” that don’t help retrieval.
     * Keep this minimal so we don't accidentally remove important lore words.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with",
            "is", "are", "was", "were", "be", "been", "being",
            "what", "who", "when", "where", "why", "how",
            "tell", "me", "about", "explain", "give"
    );

    @Override
    public Set<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        /**
         * Normalize input so extraction is consistent.
         * - lowercase: prevents missing matches due to casing
         * - strip punctuation: avoids tokens like "numidium?" vs "numidium"
         */
        String normalized = query
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " "); // keep letters/numbers/spaces, replace punctuation with spaces

        /**
         * Split into tokens.
         * We also remove stopwords and tiny tokens (like "e", "x") because
         * they add noise during retrieval.
         */
        Set<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .filter(t -> t.length() >= 3)          // drop tiny noise tokens
                .filter(t -> !STOPWORDS.contains(t))   // drop filler words
                .collect(Collectors.toCollection(HashSet::new));

        /**
         * Return a set of candidate keywords/entities.
         * Example:
         *  "What happened at the Battle of Red Mountain?"
         * -> {"happened", "battle", "red", "mountain"}
         *
         * Later improvements could:
         * - preserve multiword phrases like "red mountain"
         * - detect proper nouns more intelligently
         * - use a real NER model
         */
        return tokens;
    }
}
