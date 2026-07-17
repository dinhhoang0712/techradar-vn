package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final MentionNotifier mentionNotifier;

    public Mono<String> execute(String postId, String userId, String content, String parentId, List<String> mentionedUserIds) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Comment content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Comment content too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }
        if (MentionNotifier.tooMany(mentionedUserIds)) {
            // Validated before any write: failing after the comment already exists would leave a
            // persisted comment behind an error response.
            return Mono.error(new AppException(
                    "Too many mentions (max " + MentionNotifier.MAX_MENTIONS + ")", 400, "INVALID_MENTIONS"));
        }

        String normalizedParentId = (parentId == null || parentId.isBlank()) ? null : parentId;
        UUID commentId = UUID.randomUUID();
        UUID postUuid = UUID.fromString(postId);
        UUID userUuid = UUID.fromString(userId);
        UUID parentCommentId = normalizedParentId == null ? null : UUID.fromString(normalizedParentId);

        return validateParent(postUuid, normalizedParentId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(parentInfoOpt -> commentRepository
                        .insert(commentId, postUuid, userUuid, trimmed, parentCommentId, LocalDateTime.now())
                        .then(notifyAll(postUuid, userUuid, trimmed, parentInfoOpt.orElse(null))
                                .onErrorResume(e -> {
                                    log.warn("Could not create comment notifications for post {}", postId, e);
                                    return Mono.empty();
                                }))
                        .then(mentionNotifier.notify(userUuid, mentionedUserIds, "bình luận", "/feed")))
                .thenReturn(commentId.toString());
    }

    /** Empty if top-level (no parent). Errors (404/400) if the reply target is invalid. */
    private Mono<CommentRepository.ParentInfo> validateParent(UUID postUuid, String parentId) {
        if (parentId == null) {
            return Mono.empty();
        }
        UUID parentUuid;
        try {
            parentUuid = UUID.fromString(parentId);
        } catch (IllegalArgumentException e) {
            return Mono.error(new AppException("Invalid parent comment id", 400, "INVALID_PARENT"));
        }
        return commentRepository.findParentInfo(parentUuid)
                .switchIfEmpty(Mono.error(new NotFoundException("Parent comment not found: " + parentId)))
                .flatMap(parentInfo -> {
                    if (!parentInfo.postId().equals(postUuid)) {
                        return Mono.error(new AppException("Parent comment belongs to a different post", 400, "INVALID_PARENT"));
                    }
                    if (parentInfo.parentCommentId() != null) {
                        return Mono.error(new AppException("Cannot reply to a reply", 400, "INVALID_PARENT"));
                    }
                    return Mono.just(parentInfo);
                });
    }

    private Mono<Void> notifyAll(UUID postId, UUID commenterId, String content, CommentRepository.ParentInfo parentInfo) {
        return postRepository.findAuthorId(postId)
                .flatMap(postAuthorId -> {
                    Mono<Void> notifyPostAuthor = postAuthorId.equals(commenterId)
                            ? Mono.empty()
                            : notifyUser(postAuthorId, commenterId, "POST_COMMENT",
                                    "đã bình luận vào bài viết của bạn", preview(content));

                    Mono<Void> notifyParentAuthor = Mono.empty();
                    if (parentInfo != null) {
                        UUID parentAuthorId = parentInfo.authorId();
                        // Don't notify the post author twice for one action if they wrote the
                        // top-level comment being replied to — POST_COMMENT above already covers it.
                        boolean skip = parentAuthorId.equals(commenterId) || parentAuthorId.equals(postAuthorId);
                        if (!skip) {
                            notifyParentAuthor = notifyUser(parentAuthorId, commenterId, "COMMENT_REPLY",
                                    "đã trả lời bình luận của bạn", null);
                        }
                    }
                    return notifyPostAuthor.then(notifyParentAuthor);
                });
    }

    private Mono<Void> notifyUser(UUID targetUserId, UUID actorId, String type, String titleSuffix, String body) {
        return userRepository.findById(actorId.toString())
                .flatMap(actor -> notificationService.save(Notification.builder()
                        .userId(targetUserId)
                        .type(type)
                        .title(actor.getFullName() + " " + titleSuffix)
                        .body(body)
                        .link("/feed")
                        .read(false)
                        .build()))
                .then();
    }

    private static String preview(String content) {
        return content.length() > NOTIFICATION_PREVIEW_LENGTH
                ? content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "…"
                : content;
    }
}
