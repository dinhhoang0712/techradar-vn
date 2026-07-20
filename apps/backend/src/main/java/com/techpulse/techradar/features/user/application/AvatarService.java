package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.user.domain.Avatar;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.AvatarRepository;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.util.ImageUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stores an uploaded avatar (base64) into {@code user_avatar} and points the user's
 * {@code user_profile.avatar_url} at the serving endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarService {

    private final AvatarRepository avatarRepository;
    private final UserProfileRepository profileRepository;

    public Mono<String> upload(String userId, String contentType, String dataBase64) {
        ImageUploadValidator.Decoded decoded;
        try {
            decoded = ImageUploadValidator.validate(contentType, dataBase64);
        } catch (RuntimeException e) {
            log.warn("Avatar upload rejected for userId={}: {}", userId, e.getMessage());
            return Mono.error(e);
        }
        byte[] data = decoded.data();
        String ct = decoded.contentType();
        String url = "/api/v1/user/avatar/" + userId;

        return avatarRepository.save(userId, ct, data)
                .then(profileRepository.findByUserId(userId)
                        .defaultIfEmpty(UserProfile.builder().userId(UUID.fromString(userId)).build()))
                .map(p -> {
                    p.setUserId(UUID.fromString(userId));
                    p.setAvatarUrl(url);
                    return p;
                })
                .flatMap(profileRepository::upsert)
                .thenReturn(url)
                .doOnSuccess(u -> log.info("Avatar uploaded for userId={} ({} bytes, {})", userId, data.length, ct));
    }

    public Mono<Avatar> get(String userId) {
        return avatarRepository.find(userId);
    }
}
