package com.andrew.eldermind.lore.eval;

import java.util.*;

/**
 * Represents a single evaluation test case for retrieval benchmarking.
 *
 * Each case contains:
 *  - a user query
 *  - the document IDs expected to be retrieved
 */
public class EvaluationCase {
    
    private String query; // The query to be evaluated.
    private List<String> expectedDocs; // The list of expected relevant documents for the query.

    // No-args constructor for serialization/deserialization purposes.
    public EvaluationCase() {}

    // Constructor to initialize the evaluation case with a query and its expected relevant documents.
    public String getQuery() {
        return query;
    }

    // Getter for the query.
    public List<String> getExpectedDocs() {
        return expectedDocs;
    }

    // Getter for the expected relevant documents.
    @Override
    public String toString() {
        return "EvaluationCase{" +
                "query='" + query + '\'' +
                ", expectedDocs=" + expectedDocs +
                '}';
    }
}
