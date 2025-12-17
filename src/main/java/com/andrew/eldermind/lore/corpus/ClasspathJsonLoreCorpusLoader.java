package com.andrew.eldermind.lore.corpus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Loads the curated ElderMind lore corpus from a JSON file
 * packaged inside the application's classpath.
 *
 * This is the entry point for all lore retrieval.
 * If this loader returns an empty list, ElderMind effectively
 * operates without grounded lore (chat-only fallback).
 */
@Component
public class ClasspathJsonLoreCorpusLoader implements LoreCorpusLoader {

    /**
     * Location of the lore corpus within src/main/resources.
     * The file contains a list of LoreDocument objects.
     */
    private static final String CORPUS_PATH = "lore/lore_corpus.json";

    /**
     * Jackson ObjectMapper provided by Spring Boot.
     * Used to deserialize JSON into LoreDocument objects.
     */
    private final ObjectMapper objectMapper;

    public ClasspathJsonLoreCorpusLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads the lore corpus into memory.
     *
     * @return a list of LoreDocument entries representing the
     *         curated lore snippets ElderMind can retrieve from.
     *
     * Behavior notes:
     * - This method is intentionally simple and synchronous.
     * - The corpus is small (Sprint 2), so loading it fully into memory
     *   is acceptable and keeps retrieval fast.
     * - If loading fails, we fail gracefully by returning an empty list.
     */
    @Override
    public List<LoreDocument> load() {
        try {
            // Resolve the JSON file from the application's classpath
            ClassPathResource resource = new ClassPathResource(CORPUS_PATH);

            // Open an input stream and deserialize the JSON array
            // into a List<LoreDocument>
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(
                        in,
                        new TypeReference<List<LoreDocument>>() {}
                );
            }
        } catch (Exception e) {
            // Fail-safe behavior:
            // If the corpus cannot be loaded (missing file, malformed JSON, etc.),
            // return an empty list instead of crashing the application.
            //
            // The retriever and orchestrator are expected to handle this case
            // by falling back to a chat-only response.
            System.err.println(
                    "Failed to load lore corpus from " + CORPUS_PATH + ": " + e.getMessage()
            );
            return Collections.emptyList();
        }
    }
}
