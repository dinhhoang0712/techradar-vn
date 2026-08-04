package com.techpulse.techradar.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One {@link CircuitBreaker} bean per downstream Python service (not per client class), so all
 * clients calling the same service share one breaker and trip together when that service is
 * actually down. Config (failure-rate threshold, wait duration, sliding window) lives under
 * {@code resilience4j.circuitbreaker.instances.*} in application.yml. See
 * {@code docs/adr/0007-circuit-breaker-for-python-service-calls.md}.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreaker aiRagCoreCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("aiRagCore");
    }

    @Bean
    public CircuitBreaker mlClusteringCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("mlClustering");
    }
}
