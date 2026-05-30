package com.techpulse.techradar.config;

import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * WebFlux configuration for reactive stack setup.
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
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public WebExceptionHandler webExceptionHandler() {
        return (exchange, ex) -> {
            log.error("Unhandled exception: ", ex);

            int statusCode = 500;
            String errorCode = "INTERNAL_SERVER_ERROR";

            if (ex instanceof AppException appEx) {
                statusCode = appEx.getStatusCode();
                errorCode = appEx.getErrorCode();
            }

            ApiResponse<Void> errorResponse = ApiResponse.error(ex.getMessage(), errorCode);

            return ServerResponse
                    .status(statusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(errorResponse)
                    .switchIfEmpty(ServerResponse.status(statusCode).build()).then();
        };
    }
}
