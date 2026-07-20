package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Application service for admin-only user management: CRUD on arbitrary users, independent of
 * who is currently authenticated.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountValidator accountValidator;

    public Flux<User> listUsers() {
        return userRepository.findAll();
    }

    public Mono<User> createUser(String email,
                                 String rawPassword,
                                 String fullName,
                                 String role,
                                 String status,
                                 String subscriptionTier) {
        return accountValidator.validateEmailUnique(email, null)
                .then(Mono.defer(() -> {
                    User user = User.builder()
                            .email(email)
                            .fullName(fullName)
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .role(normalizeRole(role))
                            .status(StringUtils.hasText(status) ? status : "active")
                            .subscriptionTier(StringUtils.hasText(subscriptionTier) ? subscriptionTier : "free")
                            .build();
                    return userRepository.save(user);
                }))
                .doOnSuccess(u -> log.info("Admin created user: id={}, email={}, role={}",
                        u.getId(), u.getEmail(), u.getRole()));
    }

    public Mono<User> alterUser(String userId,
                                String email,
                                String rawPassword,
                                String fullName,
                                String role,
                                String status,
                                String subscriptionTier) {
        return accountValidator.findByIdOrThrow(userId)
                .flatMap(existing -> {
                    if (StringUtils.hasText(email) && !email.equalsIgnoreCase(existing.getEmail())) {
                        return accountValidator.validateEmailUnique(email, existing.getId().toString())
                                .then(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    if (StringUtils.hasText(email)) {
                        existing.setEmail(email);
                    }
                    if (StringUtils.hasText(fullName)) {
                        existing.setFullName(fullName);
                    }
                    if (StringUtils.hasText(rawPassword)) {
                        existing.setPasswordHash(passwordEncoder.encode(rawPassword));
                    }
                    if (StringUtils.hasText(role)) {
                        existing.setRole(normalizeRole(role));
                    }
                    if (StringUtils.hasText(status)) {
                        existing.setStatus(status);
                    }
                    if (StringUtils.hasText(subscriptionTier)) {
                        existing.setSubscriptionTier(subscriptionTier);
                    }
                    return userRepository.save(existing);
                })
                .doOnSuccess(u -> log.info("Admin updated user: id={}", u.getId()));
    }

    public Mono<Void> deleteUser(String userId) {
        return userRepository.deleteById(userId)
                .flatMap(rowsUpdated -> rowsUpdated == 0
                        ? Mono.<Void>error(new NotFoundException("User not found"))
                        : Mono.<Void>empty())
                .doOnSuccess(v -> log.info("Admin deleted user: id={}", userId));
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("admin") ? "admin" : "user";
    }
}
