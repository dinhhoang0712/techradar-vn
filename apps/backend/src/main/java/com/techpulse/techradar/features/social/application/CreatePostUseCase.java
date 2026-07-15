package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreatePostUseCase {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final PostRepository postRepository;

    public Mono<String> execute(String userId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Post content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Post content too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }

        UUID postId = UUID.randomUUID();
        return postRepository.insert(postId, UUID.fromString(userId), trimmed, LocalDateTime.now())
                .thenReturn(postId.toString());
    }
}
