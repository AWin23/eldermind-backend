package com.andrew.eldermind.lore.eval;

import com.andrew.eldermind.lore.corpus.LoreMatch;
import com.andrew.eldermind.lore.retrieval.HybridRetriever;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EvaluationRunner performs offline retrieval benchmarking for ElderMind.
 *
 * How it works:
 * - Loads a labeled evaluation dataset from JSON
 * - Runs HybridRetriever on each query
 * - Checks whether expected document IDs appear in Top-K results
 * - Prints per-query PASS / FAIL
 * - Computes Recall@K
 *
 * Why this matters:
 * - Gives us an objective way to measure retrieval quality
 * - Helps tune hybrid ranking weights
 * - Helps catch regressions if retrieval logic changes later
 * 
 */
@Component
public class EvaluationRunner implements CommandLineRunner {

    // We can adjust K for Recall@K evaluation. Common choices are 1, 3, 5, or 10.
    private static final int TOP_K = 3;

    // Path to the evaluation dataset JSON file in the classpath.
    private static final String EVAL_DATASET_PATH = "eval/retrieval-eval-set.json";
    

    private final HybridRetriever hybridRetriever; // The retriever we want to evaluate.
    private final ObjectMapper objectMapper; // For loading the evaluation dataset from JSON.

    // Constructor-based dependency injection of the HybridRetriever and ObjectMapper.
    public EvaluationRunner(HybridRetriever hybridRetriever, ObjectMapper objectMapper) {
        this.hybridRetriever = hybridRetriever;
        this.objectMapper = objectMapper;
    }

    // This method is called when the Spring Boot application starts.
    @Override
    public void run(String... args) throws Exception {

        // Optional safeguard:
        // only run evaluation when explicitly requested, e.g.
        // ./gradlew bootRun --args='eval'
        if (args.length == 0 || !"eval".equalsIgnoreCase(args[0])) {
            return;
        }

        // Load evaluation dataset from JSON file in classpath.
        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(EVAL_DATASET_PATH);

        if (inputStream == null) {
            throw new IllegalStateException(
                    "Could not find evaluation dataset at: " + EVAL_DATASET_PATH
            );
        }

        // Load evaluation cases from JSON
        List<EvaluationCase> evalCases = objectMapper.readValue(
                inputStream,
                new TypeReference<List<EvaluationCase>>() {}
        );

        int totalQueries = 0; // Total number of evaluation queries processed.
        int hitCount = 0; // Number of queries where at least one expected document was retrieved in the Top-K results.

        System.out.println("\n===== ElderMind Retrieval Evaluation =====\n");

         // debug statement to confirm that the evaluation process has completed.
        System.out.println(">>> EvaluationRunner starting evaluation...");

        // For each evaluation case, we run the query through the HybridRetriever 
        // and check if any of the expected document IDs are present in the Top-K results.
        for (EvaluationCase evalCase : evalCases) {
            totalQueries++;

            String query = evalCase.getQuery(); // The user query to be evaluated.
            List<String> expectedDocs = evalCase.getExpectedDocs(); // The list of expected relevant document IDs for the query.

            // Run hybrid retrieval
            List<LoreMatch> results = hybridRetriever.retrieveTopK(query, TOP_K);

            // LoreMatch does not expose getId(); the ID lives on the LoreDocument
            Set<String> returnedDocIds = results.stream()
                    .map(match -> match.getDocument().getId())
                    .collect(Collectors.toSet());

            // Hit if any expected doc appears in Top-K
            boolean hit = expectedDocs.stream().anyMatch(returnedDocIds::contains);

            if (hit) {
                hitCount++;
            }

            System.out.println("Query: " + query);
            System.out.println("ExpectedDocs: " + expectedDocs);
            System.out.println("TopK: " + returnedDocIds);
            System.out.println("Result: " + (hit ? "PASS" : "FAIL"));
            System.out.println("------------------------------------");
        }

        double recallAtK = totalQueries == 0 ? 0.0 : (double) hitCount / totalQueries;

        System.out.println("\n===== SUMMARY =====");
        System.out.println("Total Queries: " + totalQueries);
        System.out.println("Hits@" + TOP_K + ": " + hitCount);
        System.out.println("Recall@" + TOP_K + ": " + String.format("%.2f", recallAtK));

    }
}