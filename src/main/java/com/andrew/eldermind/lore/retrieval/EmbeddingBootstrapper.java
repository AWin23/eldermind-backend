package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreCorpusStore;
import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.gateway.EmbeddingClient;
import com.andrew.eldermind.service.EmbeddingStatusService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * EmbeddingBootstrapper
 *
 * One-time startup task that ensures each LoreDocument has an embedding vector.
 *
 * For now, we compute embeddings in-memory at startup (no persistence yet).
 * Later, you can persist embeddings back into JSON or a DB.
 */
@Component
public class EmbeddingBootstrapper implements CommandLineRunner {

    // Logger for monitoring the bootstrapping process
    private static final Logger log =
        LoggerFactory.getLogger(EmbeddingBootstrapper.class);

    // Dependencies: corpus store to access documents, embedding client to compute vectors
    private final LoreCorpusStore corpusStore;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingStatusService embeddingStatusService;


    public EmbeddingBootstrapper(LoreCorpusStore corpusStore,
                                 EmbeddingClient embeddingClient,
                                 EmbeddingStatusService embeddingStatusService) {
        this.corpusStore = corpusStore;
        this.embeddingClient = embeddingClient;
        this.embeddingStatusService = embeddingStatusService;
    }

    @Override
    public void run(String... args) {
        List<LoreDocument> corpus = corpusStore.getCorpus();

        long missing = corpus.stream()
                .filter(doc -> doc.getEmbedding() == null || doc.getEmbedding().isEmpty())
                .count();

        log.info("==================================================");
        log.info(
            "EmbeddingBootstrapper corpusSize={} missingEmbeddings={}",
            corpus.size(),
            missing
        );
        log.info("==================================================");
        
        for (LoreDocument doc : corpus) {
            if (doc.getEmbedding() != null && !doc.getEmbedding().isEmpty()) continue;
            
            // Use a compact but informative input for the doc vector.
            String input = (safe(doc.getTitle()) + "\n\n" + safe(doc.getText())).trim();
            
            // Call embeddings API once per doc
            doc.setEmbedding(embeddingClient.embed(input));
        }
        
        // Final log after bootstrapping to confirm all embeddings are set
        log.info("==================================================");
        log.info(
            "EmbeddingBootstrapper done embeddedDocs={}",
            corpus.size()
    );
    log.info("==================================================");

    // Mark embeddings as ready so retrieval can proceed
    embeddingStatusService.markReady();
}

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}