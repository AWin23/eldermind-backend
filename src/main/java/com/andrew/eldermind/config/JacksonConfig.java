package com.andrew.eldermind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central Jackson configuration.
 *
 * We explicitly provide an ObjectMapper bean so any component
 * (lore loaders, DTO mappers, etc.) can depend on it safely.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
