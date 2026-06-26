package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.auth.adapters.input.LoginRequest;
import com.techpulse.techradar.features.auth.adapters.input.LoginResponse;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Login use case - orchestrates authentication logic.
 * Pure application layer with no HTTP concerns.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Mono<LoginResponse> execute(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Login failed: no account for email={}", request.getEmail());
                    return Mono.error(new InvalidCredentialsException("Invalid email or password"));
                }))
                .flatMap(user -> validateAndGenerateTokens(user, request.getPassword()))
                .doOnSuccess(response -> log.info("Login successful for email={} userId={}",
                        response.getEmail(), response.getUserId()))
                .doOnError(InvalidCredentialsException.class,
                        e -> log.warn("Login failed for email={}: {}", request.getEmail(), e.getMessage()));
    }

    private Mono<LoginResponse> validateAndGenerateTokens(User user, String rawPassword) {
        return Mono.fromCallable(() -> {
            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new InvalidCredentialsException("Invalid email or password");
            }
            if (!user.isActive()) {
                throw new InvalidCredentialsException("User account is inactive");
            }
            return user;
        }).map(this::createLoginResponse);
    }

    private LoginResponse createLoginResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole())
                .expiresIn(jwtTokenProvider.getExpirationTime(accessToken) - System.currentTimeMillis())
                .build();
    }
}
