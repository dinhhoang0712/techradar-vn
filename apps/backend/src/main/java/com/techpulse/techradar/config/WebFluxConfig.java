package com.techpulse.techradar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * WebFlux configuration for reactive stack setup.
 * <p>
 * Exception-to-HTTP mapping lives in {@code shared.exception.GlobalExceptionHandler}
 * ({@code @RestControllerAdvice}); non-application errors fall back to Spring Boot's default handler.
 */
@Slf4j
@Configuration
public class WebFluxConfig {

    @Value("${spring.webflux.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${spring.webflux.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        // "*" cannot be combined with allowCredentials=true; fall back to origin patterns.
        if (origins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(m -> !m.isEmpty())
                .toList());
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
