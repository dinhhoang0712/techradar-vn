package com.techpulse.techradar.config.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts a Bearer token from the {@code Authorization} header and wraps it in an
 * unauthenticated {@link Authentication} for the {@link JwtReactiveAuthenticationManager} to validate.
 */
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Mono.empty();
        }
        // Raw token carried as both principal and credentials; the manager replaces it once validated.
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
