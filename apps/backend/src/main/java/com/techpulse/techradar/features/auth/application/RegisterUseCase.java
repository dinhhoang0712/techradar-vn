package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.adapters.input.LoginResponse;
import com.techpulse.techradar.features.auth.adapters.input.RegisterRequest;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Register use case - orchestrates user registration logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public Mono<LoginResponse> execute(RegisterRequest request) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Registration rejected: email already registered: {}", request.getEmail());
                        return Mono.error(
                                new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered")
                        );
                    }
                    return createUser(request);
                });
    }

    private Mono<LoginResponse> createUser(RegisterRequest request) {
        User newUser = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .status("active")
                .subscriptionTier(request.getSubscriptionTier() != null ?
                        request.getSubscriptionTier() : "free")
                .build();

        return userRepository.save(newUser)
                .doOnNext(user -> log.info("User registered: id={}, email={}, tier={}",
                        user.getId(), user.getEmail(), user.getSubscriptionTier()))
                .map(tokenIssuer::issueFor);
    }
}
