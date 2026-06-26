package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.user.adapters.input.UpdateProfileRequest;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.UUID;

/**
 * Application service for user and account management.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    // ---- Self-service profile -------------------------------------------------

    public Mono<User> getProfile(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")));
    }

    public Mono<ProfileData> getProfileData(String userId) {
        return getProfile(userId)
                .flatMap(user -> profileRepository.findByUserId(userId)
                        .defaultIfEmpty(emptyProfile(userId))
                        .map(profile -> new ProfileData(user, profile)));
    }

    public Mono<ProfileData> updateProfile(String userId, UpdateProfileRequest request) {
        return getProfile(userId)
                .flatMap(user -> applyAccountChanges(user, request))
                .flatMap(userRepository::save)
                .flatMap(savedUser -> profileRepository.findByUserId(userId)
                        .defaultIfEmpty(emptyProfile(userId))
                        .map(existing -> mergeProfile(existing, userId, request))
                        .flatMap(profileRepository::upsert)
                        .map(savedProfile -> new ProfileData(savedUser, savedProfile)))
                .doOnSuccess(pd -> log.info("Profile updated for userId={}", userId));
    }

    private Mono<User> applyAccountChanges(User user, UpdateProfileRequest request) {
        Mono<User> pipeline = Mono.just(user);
        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            pipeline = validateEmailUnique(request.getEmail(), user.getId().toString())
                    .then(Mono.fromRunnable(() -> user.setEmail(request.getEmail())))
                    .thenReturn(user);
        }
        return pipeline.map(u -> {
            if (StringUtils.hasText(request.getFullName())) {
                u.setFullName(request.getFullName());
            }
            if (StringUtils.hasText(request.getPassword())) {
                u.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            }
            if (StringUtils.hasText(request.getSubscriptionTier())) {
                u.setSubscriptionTier(request.getSubscriptionTier());
            }
            return u;
        });
    }

    private UserProfile mergeProfile(UserProfile existing, String userId, UpdateProfileRequest request) {
        return UserProfile.builder()
                .userId(UUID.fromString(userId))
                .jobRole(request.getJobRole() != null ? request.getJobRole() : existing.getJobRole())
                .location(request.getLocation() != null ? request.getLocation() : existing.getLocation())
                .bio(request.getBio() != null ? request.getBio() : existing.getBio())
                .avatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : existing.getAvatarUrl())
                .technologies(request.getTechnologies() != null ? request.getTechnologies() : existing.getTechnologies())
                .notifyInapp(request.getNotifyInapp() != null ? request.getNotifyInapp()
                        : (existing.getNotifyInapp() != null ? existing.getNotifyInapp() : Boolean.TRUE))
                .notifyEmail(request.getNotifyEmail() != null ? request.getNotifyEmail()
                        : (existing.getNotifyEmail() != null ? existing.getNotifyEmail() : Boolean.TRUE))
                .build();
    }

    private UserProfile emptyProfile(String userId) {
        return UserProfile.builder()
                .userId(UUID.fromString(userId))
                .notifyInapp(true)
                .notifyEmail(true)
                .build();
    }

    // ---- Admin user management -----------------------------------------------

    public Flux<User> listUsers() {
        return userRepository.findAll();
    }

    public Mono<User> createUser(String email,
                                 String rawPassword,
                                 String fullName,
                                 String role,
                                 String status,
                                 String subscriptionTier) {
        return validateEmailUnique(email, null)
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
        return getProfile(userId)
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

    // ---- Helpers --------------------------------------------------------------

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
