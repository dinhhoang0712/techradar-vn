package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resets a user's password given a valid, unused, non-expired reset token.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<Void> execute(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            log.warn("Password reset rejected: new password does not meet minimum length");
            return Mono.error(new AppException("Password must be at least 8 characters", 400, "INVALID_PASSWORD"));
        }
        if (token == null || !isUuid(token)) {
            log.warn("Password reset rejected: malformed or missing reset token");
            return Mono.error(new AppException("Invalid or expired token", 400, "INVALID_TOKEN"));
        }
        return passwordResetRepository.findValidUserId(token)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Password reset rejected: token invalid, expired or already used");
                    return Mono.error(new AppException("Invalid or expired token", 400, "INVALID_TOKEN"));
                }))
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Password reset failed: user not found for userId={}", userId);
                            return Mono.error(new AppException("Invalid or expired token", 400, "INVALID_TOKEN"));
                        }))
                        .flatMap(user -> {
                            user.setPasswordHash(passwordEncoder.encode(newPassword));
                            return userRepository.save(user);
                        })
                        .then(passwordResetRepository.markUsed(token))
                        .doOnSuccess(v -> log.info("Password reset successful for userId={}", userId)));
    }

    private static boolean isUuid(String v) {
        try {
            UUID.fromString(v);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
