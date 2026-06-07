package com.andrew.eldermind.service;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingStatusService {

    private volatile boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        ready = true;
    }
}
