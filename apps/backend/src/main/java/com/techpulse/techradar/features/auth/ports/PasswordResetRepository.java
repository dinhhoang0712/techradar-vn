package com.techpulse.techradar.features.auth.ports;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port for password-reset tokens ({@code password_reset} table).
 */
public interface PasswordResetRepository {

    Mono<UUID> createToken(String userId);

    /** @return the user id for a token that exists, is unused and not expired; empty otherwise. */
    Mono<String> findValidUserId(String token);

    Mono<Void> markUsed(String token);
}
