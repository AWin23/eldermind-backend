package com.andrew.eldermind.lore.corpus;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LoreCorpusStore
 *
 * Single source of truth for the in-memory lore corpus.
 *
 * IMPORTANT:
 * - We load the corpus exactly once at startup.
 * - All retrievers and bootstrappers reference this same List instance.
 * - That ensures any mutation (like adding embeddings) is visible everywhere.
 */
@Service
public class LoreCorpusStore {

    private final List<LoreDocument> corpus;

    public LoreCorpusStore(LoreCorpusLoader loader) {
        // Load once during bean construction (startup-time)
        this.corpus = loader.load();

        // Sanity check log to confirm corpus loaded correctly
        Logger log = LoggerFactory.getLogger(LoreCorpusStore.class);
        log.info("LoreCorpusStore loaded corpusSize={}", corpus.size());
    }

    public List<LoreDocument> getCorpus() {
        return corpus;
    }
}