package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToggleLikeUseCase {

    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public Mono<Void> like(String postId, String userId) {
        UUID postUuid = UUID.fromString(postId);
        UUID userUuid = UUID.fromString(userId);
        return postRepository.like(postUuid, userUuid)
                .flatMap(isNewLike -> {
                    if (!isNewLike) {
                        return Mono.empty();
                    }
                    return notifyLike(postUuid, userUuid)
                            .onErrorResume(e -> {
                                log.warn("Could not create POST_LIKE notification for post {}", postId, e);
                                return Mono.empty();
                            });
                })
                .then();
    }

    public Mono<Void> unlike(String postId, String userId) {
        return postRepository.unlike(UUID.fromString(postId), UUID.fromString(userId));
    }

    private Mono<Void> notifyLike(UUID postId, UUID likerId) {
        return postRepository.findAuthorId(postId)
                .filter(authorId -> !authorId.equals(likerId))
                .flatMap(authorId -> userRepository.findById(likerId.toString())
                        .flatMap(liker -> notificationService.save(Notification.builder()
                                .userId(authorId)
                                .type("POST_LIKE")
                                .title(liker.getFullName() + " đã thích bài viết của bạn")
                                .link("/feed")
                                .read(false)
                                .build())))
                .then();
    }
}
