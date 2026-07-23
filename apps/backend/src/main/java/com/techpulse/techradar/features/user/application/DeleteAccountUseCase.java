package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * GDPR self-service account deletion ("right to erasure"). Requires the current password so a
 * still-valid-but-hijacked session can't be used to destroy the account. The user row is deleted
 * outright; every table with a {@code REFERENCES users(id) ON DELETE CASCADE} (posts, comments,
 * chat history, messages, notifications, follows...) is cleaned up by the database itself.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeleteAccountUseCase {

    private final UserAccountValidator accountValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityStampService securityStampService;

    public Mono<Void> execute(String userId, String currentPassword) {
        return accountValidator.findByIdOrThrow(userId)
                .flatMap(user -> {
                    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                        return Mono.error(new InvalidCredentialsException("Current password is incorrect"));
                    }
                    // Bump the Redis-backed security stamp before the row disappears, so any
                    // other still-valid access token for this user is rejected on its very next
                    // request instead of remaining usable until it naturally expires - the stamp
                    // check doesn't require the Postgres row to still exist.
                    return securityStampService.set(userId, UUID.randomUUID())
                            .then(userRepository.deleteById(userId));
                })
                .doOnSuccess(v -> log.info("Self-service account deletion for userId={}", userId))
                .then();
    }
}
