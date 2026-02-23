package com.andrew.eldermind.dto;

public enum FallbackReason {

    // Reasons for skipping retrieval or not using retrieved evidence
    EMPTY_CORPUS,

    // Keyword retriever specific reasons
    NO_KEYWORDS,

    // Embedding retriever specific reasons
    NO_MATCHES,

    // Common reasons
    BELOW_THRESHOLD,

    // Operational reasons (e.g. config flags, errors)
    DISABLED_BY_FLAG,

    // Unexpected errors during retrieval (e.g. exceptions, timeouts)
    ERROR,

    // For cases where retrieval was attempted but the top score was just very low, indicating weak relevance
    LOW_CONFIDENCE,

    // When the retrieved evidence is not directly relevant to the question, even if it has a decent score. 
    // This can be determined by checking for the presence of key terms from the question in the retrieved evidence.
    NO_HARD_EVIDENCE
}
