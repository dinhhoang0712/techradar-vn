package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AddCommentUseCase {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final CommentRepository commentRepository;

    public Mono<String> execute(String postId, String userId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Comment content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Comment content too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }

        UUID commentId = UUID.randomUUID();
        return commentRepository.insert(commentId, UUID.fromString(postId), UUID.fromString(userId), trimmed, LocalDateTime.now())
                .thenReturn(commentId.toString());
    }
}
