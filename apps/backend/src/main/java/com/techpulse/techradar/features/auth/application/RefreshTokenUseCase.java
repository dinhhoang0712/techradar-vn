package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.ports.TokenValidator;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import com.techpulse.techradar.shared.redis.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Refresh token use case - validates refresh token and generates new access token.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklist;
    private final TokenIssuer tokenIssuer;

    public Mono<LoginResponse> execute(String refreshToken) {
        return tokenBlacklist.isBlacklisted(refreshToken)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return Mono.error(new InvalidCredentialsException("Refresh token has been revoked"));
                    }
                    return Mono.fromCallable(() -> {
                        if (!tokenValidator.isValid(refreshToken)) {
                            throw new InvalidCredentialsException("Invalid or expired refresh token");
                        }
                        if (!tokenValidator.isRefreshToken(refreshToken)) {
                            throw new InvalidCredentialsException("Token is not a refresh token");
                        }
                        return tokenValidator.extractUserId(refreshToken);
                    });
                })
                .flatMap(userId ->
                userRepository.findById(userId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Refresh token failed: user not found userId={}", userId);
                            return Mono.error(new InvalidCredentialsException("User not found"));
                        }))
                        // Without this check a banned/deactivated user could keep calling
                        // /auth/refresh to mint brand-new, fully valid access tokens (with the
                        // current security stamp already embedded) for as long as their refresh
                        // token remains unexpired - completely bypassing the ban.
                        .flatMap(user -> user.isActive()
                                ? tokenIssuer.issueFor(user)
                                : Mono.error(new InvalidCredentialsException("User account is inactive")))
        )
        .doOnSuccess(response -> log.info("Access token refreshed for userId={}", response.getUserId()))
        .doOnError(InvalidCredentialsException.class,
                e -> log.warn("Refresh token rejected: {}", e.getMessage()));
    }
}
