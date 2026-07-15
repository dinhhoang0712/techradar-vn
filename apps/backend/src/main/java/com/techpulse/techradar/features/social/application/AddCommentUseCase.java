package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddCommentUseCase {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int NOTIFICATION_PREVIEW_LENGTH = 140;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public Mono<String> execute(String postId, String userId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Comment content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Comment content too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }

        UUID commentId = UUID.randomUUID();
        UUID postUuid = UUID.fromString(postId);
        UUID userUuid = UUID.fromString(userId);
        return commentRepository.insert(commentId, postUuid, userUuid, trimmed, LocalDateTime.now())
                .then(notifyComment(postUuid, userUuid, trimmed)
                        .onErrorResume(e -> {
                            log.warn("Could not create POST_COMMENT notification for post {}", postId, e);
                            return Mono.empty();
                        }))
                .thenReturn(commentId.toString());
    }

    private Mono<Void> notifyComment(UUID postId, UUID commenterId, String content) {
        return postRepository.findAuthorId(postId)
                .filter(authorId -> !authorId.equals(commenterId))
                .flatMap(authorId -> userRepository.findById(commenterId.toString())
                        .flatMap(commenter -> notificationService.save(Notification.builder()
                                .userId(authorId)
                                .type("POST_COMMENT")
                                .title(commenter.getFullName() + " đã bình luận vào bài viết của bạn")
                                .body(preview(content))
                                .link("/feed")
                                .read(false)
                                .build())))
                .then();
    }

    private static String preview(String content) {
        return content.length() > NOTIFICATION_PREVIEW_LENGTH
                ? content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "…"
                : content;
    }
}
