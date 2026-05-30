package com.techpulse.techradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * TechRadar Spring Boot application entry point.
 * Hexagonal Architecture with feature-based module organization.
 */
@SpringBootApplication
public class TechRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechRadarApplication.class, args);
    }
}
