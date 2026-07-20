package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.user.adapters.input.UpdateProfileRequest;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for self-service profile operations: retrieving and updating the
 * currently-authenticated user's own account and extended profile.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountValidator accountValidator;

    public Mono<User> getProfile(String userId) {
        return accountValidator.findByIdOrThrow(userId);
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
            pipeline = accountValidator.validateEmailUnique(request.getEmail(), user.getId().toString())
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
}
