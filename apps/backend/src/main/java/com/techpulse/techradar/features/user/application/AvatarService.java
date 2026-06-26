package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.user.domain.Avatar;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.AvatarRepository;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Stores an uploaded avatar (base64) into {@code user_avatar} and points the user's
 * {@code user_profile.avatar_url} at the serving endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarService {

    private static final int MAX_BYTES = 3 * 1024 * 1024; // 3 MB
    // Raster-only allowlist: no image/svg+xml (SVG can carry script -> stored XSS on the public serve endpoint).
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    private final AvatarRepository avatarRepository;
    private final UserProfileRepository profileRepository;

    public Mono<String> upload(String userId, String contentType, String dataBase64) {
        byte[] data;
        try {
            String base64 = dataBase64 == null ? "" : dataBase64;
            int comma = base64.indexOf(','); // strip data URL prefix if present
            if (comma >= 0) {
                base64 = base64.substring(comma + 1);
            }
            data = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return Mono.error(new AppException("Invalid base64 image", 400, "INVALID_IMAGE"));
        }
        if (data.length == 0 || data.length > MAX_BYTES) {
            log.warn("Avatar upload rejected for userId={}: empty or too large ({} bytes)", userId, data.length);
            return Mono.error(new AppException("Image empty or too large (max 3MB)", 400, "INVALID_IMAGE"));
        }

        String ct = (contentType == null || contentType.isBlank()) ? "image/png" : contentType.toLowerCase().trim();
        if (!ALLOWED_TYPES.contains(ct)) {
            log.warn("Avatar upload rejected for userId={}: unsupported type {}", userId, ct);
            return Mono.error(new AppException("Unsupported image type (png/jpeg/webp/gif only)", 400, "INVALID_IMAGE"));
        }
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
