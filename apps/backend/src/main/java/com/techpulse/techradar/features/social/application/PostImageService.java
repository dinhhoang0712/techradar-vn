package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.adapters.input.SocialDtos;
import com.techpulse.techradar.features.social.ports.PostImageRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.util.ImageUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
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

    private final PostImageRepository postImageRepository;

    /** @throws BadRequestException if the count/size/type of any image is invalid. */
    public List<PreparedImage> validate(List<SocialDtos.ImageInput> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES_PER_POST) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Too many images (max " + MAX_IMAGES_PER_POST + ")");
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
        ImageUploadValidator.Decoded decoded;
        try {
            decoded = ImageUploadValidator.validate(image.getContentType(), image.getDataBase64());
        } catch (RuntimeException e) {
            log.warn("Post image rejected: {}", e.getMessage());
            throw e;
        }
        return new PreparedImage(decoded.contentType(), decoded.data());
    }

    public record PreparedImage(String contentType, byte[] data) {
    }
}
