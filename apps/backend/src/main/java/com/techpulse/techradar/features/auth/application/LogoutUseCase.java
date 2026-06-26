package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.shared.redis.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Blacklists the provided refresh token so it cannot be used to issue new access tokens.
 * TTL matches the token's remaining lifetime so the Redis entry self-expires.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LogoutUseCase {

    private final TokenBlacklistService blacklist;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.redis.token-blacklist-ttl:604800}")
    private long maxTtlSeconds;

    public Mono<Void> execute(String refreshToken) {
        return Mono.fromCallable(() -> remainingTtl(refreshToken))
                .flatMap(ttl -> blacklist.blacklist(refreshToken, ttl))
                .doOnSuccess(v -> log.info("Refresh token blacklisted on logout"))
                .doOnError(e -> log.warn("Failed to blacklist token on logout: {}", e.getMessage()));
    }

    private Duration remainingTtl(String token) {
        try {
            long expiryMs = jwtTokenProvider.getExpirationTime(token);
            long remainingMs = expiryMs - System.currentTimeMillis();
            long seconds = Math.max(1, Math.min(remainingMs / 1000, maxTtlSeconds));
            return Duration.ofSeconds(seconds);
        } catch (Exception e) {
            return Duration.ofSeconds(maxTtlSeconds);
        }
    }
}