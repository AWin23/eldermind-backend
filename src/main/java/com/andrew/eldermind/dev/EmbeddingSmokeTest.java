package com.andrew.eldermind.dev;

import com.andrew.eldermind.lore.gateway.EmbeddingClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A simple smoke test to verify EmbeddingClient works and returns a vector
 * of expected length. Only runs in the "dev" Spring profile.
 */
@Profile("dev")
@Component
public class EmbeddingSmokeTest implements CommandLineRunner {

    private final EmbeddingClient embeddingClient;

    public EmbeddingSmokeTest(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void run(String... args) {
        List<Double> v = embeddingClient.embed("Battle of Red Mountain");
        System.out.println("[EmbeddingSmokeTest] vector length=" + v.size());
        System.out.println("[EmbeddingSmokeTest] first3=" +
                v.subList(0, Math.min(3, v.size())));
    }
}