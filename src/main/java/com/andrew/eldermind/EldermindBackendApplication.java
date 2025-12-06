package com.andrew.eldermind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.andrew.eldermind")
public class EldermindBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EldermindBackendApplication.class, args);
    }
}
