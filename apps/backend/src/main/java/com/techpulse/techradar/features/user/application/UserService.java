package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Application service for user and account management.
 */
@Component
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<User> getProfile(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")));
    }

    public Mono<User> updateProfile(String userId, String email, String subscriptionTier) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(existing -> {
                    if (StringUtils.hasText(email) && !email.equalsIgnoreCase(existing.getEmail())) {
                        return validateEmailUnique(email, existing.getId().toString())
                                .then(Mono.just(existing))
                                .map(user -> {
                                    user.setEmail(email);
                                    return user;
                                });
                    }
                    return Mono.just(existing);
                })
                .map(existing -> {
                    if (StringUtils.hasText(subscriptionTier)) {
                        existing.setSubscriptionTier(subscriptionTier);
                    }
                    return existing;
                })
                .flatMap(userRepository::save);
    }

    public Flux<User> listUsers() {
        return userRepository.findAll();
    }

    public Mono<User> createUser(String email,
                                 String rawPassword,
                                 String role,
                                 String status,
                                 String subscriptionTier) {
        return validateEmailUnique(email, null)
                .then(Mono.defer(() -> {
                    User user = User.builder()
                            .email(email)
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .role(normalizeRole(role))
                            .status(StringUtils.hasText(status) ? status : "active")
                            .subscriptionTier(StringUtils.hasText(subscriptionTier) ? subscriptionTier : "free")
                            .build();
                    return userRepository.save(user);
                }));
    }

    public Mono<User> alterUser(String userId,
                                String email,
                                String rawPassword,
                                String role,
                                String status,
                                String subscriptionTier) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(existing -> {
                    if (StringUtils.hasText(email) && !email.equalsIgnoreCase(existing.getEmail())) {
                        return validateEmailUnique(email, existing.getId().toString())
                                .then(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    if (StringUtils.hasText(email)) {
                        existing.setEmail(email);
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
                });
    }

    public Mono<Void> deleteUser(String userId) {
        return userRepository.deleteById(userId)
                .flatMap(rowsUpdated -> rowsUpdated == 0
                        ? Mono.error(new NotFoundException("User not found"))
                        : Mono.empty());
    }

    private Mono<Void> validateEmailUnique(String email, String currentUserId) {
        return userRepository.findByEmail(email)
                .flatMap(found -> {
                    if (currentUserId == null || !found.getId().toString().equals(currentUserId)) {
                        return Mono.error(new AppException("Email already registered", 409, "EMAIL_ALREADY_EXISTS"));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("admin") ? "admin" : "user";
    }
}
