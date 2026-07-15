package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToggleFollowUseCase {

    private final FollowRepository followRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public Mono<Void> follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            return Mono.error(new AppException("Cannot follow yourself", 400, "INVALID_FOLLOW"));
        }
        return followRepository.follow(UUID.fromString(followerId), UUID.fromString(followeeId))
                .flatMap(isNewFollow -> {
                    if (!isNewFollow) {
                        return Mono.empty();
                    }
                    return notifyFollow(followerId, followeeId)
                            .onErrorResume(e -> {
                                log.warn("Could not create NEW_FOLLOWER notification for followee {}", followeeId, e);
                                return Mono.empty();
                            });
                })
                .then();
    }

    public Mono<Void> unfollow(String followerId, String followeeId) {
        return followRepository.unfollow(UUID.fromString(followerId), UUID.fromString(followeeId));
    }

    private Mono<Void> notifyFollow(String followerId, String followeeId) {
        return userRepository.findById(followerId)
                .flatMap(follower -> notificationService.save(Notification.builder()
                        .userId(UUID.fromString(followeeId))
                        .type("NEW_FOLLOWER")
                        .title(follower.getFullName() + " đã bắt đầu theo dõi bạn")
                        .link("/users/" + followerId)
                        .read(false)
                        .build()))
                .then();
    }
}
