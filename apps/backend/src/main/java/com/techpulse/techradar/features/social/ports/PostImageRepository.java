package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PostImageRepository {

    Mono<Void> insert(UUID id, UUID postId, int ordinal, String contentType, byte[] data, LocalDateTime createdAt);

    Mono<ImageRow> findById(UUID imageId);

    record ImageRow(UUID postId, String contentType, byte[] data) {
    }
}
