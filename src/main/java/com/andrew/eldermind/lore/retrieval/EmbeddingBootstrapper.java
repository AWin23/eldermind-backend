package com.andrew.eldermind.lore.retrieval;

import com.andrew.eldermind.lore.corpus.LoreCorpusStore;
import com.andrew.eldermind.lore.corpus.LoreDocument;
import com.andrew.eldermind.lore.gateway.EmbeddingClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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

    private final LoreCorpusStore corpusStore;
    private final EmbeddingClient embeddingClient;

    public EmbeddingBootstrapper(LoreCorpusStore corpusStore,
                                 EmbeddingClient embeddingClient) {
        this.corpusStore = corpusStore;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void run(String... args) {
        List<LoreDocument> corpus = corpusStore.getCorpus();

        long missing = corpus.stream()
                .filter(doc -> doc.getEmbedding() == null || doc.getEmbedding().isEmpty())
                .count();

        System.out.println("[EmbeddingBootstrapper] corpus size=" + corpus.size()
                + " missingEmbeddings=" + missing);

        for (LoreDocument doc : corpus) {
            if (doc.getEmbedding() != null && !doc.getEmbedding().isEmpty()) continue;

            // Use a compact but informative input for the doc vector.
            String input = (safe(doc.getTitle()) + "\n\n" + safe(doc.getText())).trim();

            // Call embeddings API once per doc
            doc.setEmbedding(embeddingClient.embed(input));
        }

        System.out.println("[EmbeddingBootstrapper] done. embeddedDocs=" + corpus.size());
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}