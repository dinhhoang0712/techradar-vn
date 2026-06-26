package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.auth.adapters.input.LoginResponse;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklist;

    public Mono<LoginResponse> execute(String refreshToken) {
        return tokenBlacklist.isBlacklisted(refreshToken)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return Mono.error(new InvalidCredentialsException("Refresh token has been revoked"));
                    }
                    return Mono.fromCallable(() -> {
                        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
                            throw new InvalidCredentialsException("Invalid or expired refresh token");
                        }
                        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
                            throw new InvalidCredentialsException("Token is not a refresh token");
                        }
                        return jwtTokenProvider.getUserIdFromToken(refreshToken);
                    });
                })
                .flatMap(userId ->
                userRepository.findById(userId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Refresh token failed: user not found userId={}", userId);
                            return Mono.error(new InvalidCredentialsException("User not found"));
                        }))
                        .map(user -> {
                            String newAccessToken = jwtTokenProvider.generateToken(
                                    user.getId().toString(),
                                    user.getEmail(),
                                    user.getRole()
                            );
                            String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                                    user.getId().toString()
                            );

                            return LoginResponse.builder()
                                    .accessToken(newAccessToken)
                                    .refreshToken(newRefreshToken)
                                    .userId(user.getId().toString())
                                    .email(user.getEmail())
                                    .role(user.getRole())
                                    .expiresIn(jwtTokenProvider.getExpirationTime(newAccessToken) -
                                            System.currentTimeMillis())
                                    .build();
                        })
        )
        .doOnSuccess(response -> log.info("Access token refreshed for userId={}", response.getUserId()))
        .doOnError(InvalidCredentialsException.class,
                e -> log.warn("Refresh token rejected: {}", e.getMessage()));
    }
}
