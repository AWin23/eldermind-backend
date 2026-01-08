package com.andrew.eldermind.dto;

public enum FallbackReason {
    EMPTY_CORPUS,
    NO_KEYWORDS,
    NO_MATCHES,
    BELOW_THRESHOLD,
    DISABLED_BY_FLAG,
    ERROR,
}
