package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.adapters.input.SocialDtos;
import com.techpulse.techradar.features.social.ports.PostImageRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stores post image attachments (base64 in, BYTEA storage) — same shape as
 * {@link com.techpulse.techradar.features.user.application.AvatarService}, generalized to up to
 * {@value #MAX_IMAGES_PER_POST} images ordered per post instead of one image per user.
 * <p>
 * Split into {@link #validate} (synchronous, throws) and {@link #persist} (the actual write) so a
 * caller can validate BEFORE writing anything else (e.g. the post row itself) — validating only at
 * insert time would let an invalid image request leave a persisted post behind an error response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageService {

    private static final int MAX_IMAGES_PER_POST = 4;
    private static final int MAX_BYTES_PER_IMAGE = 3 * 1024 * 1024; // 3 MB
    // Raster-only allowlist: no image/svg+xml (SVG can carry script -> stored XSS on the public serve endpoint).
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    private final PostImageRepository postImageRepository;

    /** @throws AppException if the count/size/type of any image is invalid. */
    public List<PreparedImage> validate(List<SocialDtos.ImageInput> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES_PER_POST) {
            throw new AppException("Too many images (max " + MAX_IMAGES_PER_POST + ")", 400, "INVALID_IMAGE");
        }
        return images.stream().map(this::decode).toList();
    }

    /** Inserts already-{@link #validate}d images, preserving order. */
    public Mono<Void> persist(UUID postId, List<PreparedImage> images) {
        if (images == null || images.isEmpty()) {
            return Mono.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        return Flux.range(0, images.size())
                .concatMap(i -> postImageRepository.insert(
                        UUID.randomUUID(), postId, i, images.get(i).contentType(), images.get(i).data(), now))
                .then();
    }

    public Mono<PostImageRepository.ImageRow> get(UUID imageId) {
        return postImageRepository.findById(imageId);
    }

    private PreparedImage decode(SocialDtos.ImageInput image) {
        String base64 = image.getDataBase64() == null ? "" : image.getDataBase64();
        int comma = base64.indexOf(','); // strip data URL prefix if present
        if (comma >= 0) {
            base64 = base64.substring(comma + 1);
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException("Invalid base64 image", 400, "INVALID_IMAGE");
        }
        if (data.length == 0 || data.length > MAX_BYTES_PER_IMAGE) {
            log.warn("Post image rejected: empty or too large ({} bytes)", data.length);
            throw new AppException("Image empty or too large (max 3MB)", 400, "INVALID_IMAGE");
        }

        String ct = image.getContentType() == null || image.getContentType().isBlank()
                ? "image/png" : image.getContentType().toLowerCase().trim();
        if (!ALLOWED_TYPES.contains(ct)) {
            log.warn("Post image rejected: unsupported type {}", ct);
            throw new AppException("Unsupported image type (png/jpeg/webp/gif only)", 400, "INVALID_IMAGE");
        }
        return new PreparedImage(ct, data);
    }

    public record PreparedImage(String contentType, byte[] data) {
    }
}
