package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import com.techpulse.techradar.shared.util.UuidUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    private final SecurityStampService securityStampService;

    public Mono<Void> execute(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            log.warn("Password reset rejected: new password does not meet minimum length");
            return Mono.error(new BadRequestException(ErrorCode.INVALID_PASSWORD, "Password must be at least 8 characters"));
        }
        if (token == null || !UuidUtils.isValid(token)) {
            log.warn("Password reset rejected: malformed or missing reset token");
            return Mono.error(new BadRequestException(ErrorCode.INVALID_TOKEN, "Invalid or expired token"));
        }
        return passwordResetRepository.findValidUserId(token)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Password reset rejected: token invalid, expired or already used");
                    return Mono.error(new BadRequestException(ErrorCode.INVALID_TOKEN, "Invalid or expired token"));
                }))
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Password reset failed: user not found for userId={}", userId);
                            return Mono.error(new BadRequestException(ErrorCode.INVALID_TOKEN, "Invalid or expired token"));
                        }))
                        .flatMap(user -> {
                            // Rotates securityStamp internally - see User.changePassword().
                            user.changePassword(passwordEncoder.encode(newPassword));
                            return userRepository.save(user)
                                    .flatMap(saved -> securityStampService
                                            .set(saved.getId().toString(), saved.getSecurityStamp()));
                        })
                        .then(passwordResetRepository.markUsed(token))
                        .doOnSuccess(v -> log.info("Password reset successful for userId={}", userId)));
    }
}
