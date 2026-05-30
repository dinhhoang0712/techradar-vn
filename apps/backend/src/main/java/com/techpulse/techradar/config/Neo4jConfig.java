package com.techpulse.techradar.config;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j Java Driver configuration.
 * Direct driver usage for full control over Cypher queries.
 */
@Configuration
@RequiredArgsConstructor
public class Neo4jConfig {

    @Value("${app.neo4j.uri}")
    private String neo4jUri;

    @Value("${app.neo4j.username}")
    private String neo4jUsername;

    @Value("${app.neo4j.password}")
    private String neo4jPassword;

    @Value("${app.neo4j.pool-size:50}")
    private int poolSize;

    @Value("${app.neo4j.acquisition-timeout:60000}")
    private long acquisitionTimeout;

    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(
                neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword),
                org.neo4j.driver.Config.builder()
                        .withMaxConnectionPoolSize(poolSize)
                        .withConnectionAcquisitionTimeout(
                                acquisitionTimeout,
                                TimeUnit.MILLISECONDS
                        )
                        .build()
        );
    }
}
