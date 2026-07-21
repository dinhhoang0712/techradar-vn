package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.notification.application.ActivityNotifier;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
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
    private final ActivityNotifier activityNotifier;

    public Mono<Void> follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_FOLLOW, "Cannot follow yourself"));
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
        return activityNotifier.notify(
                UUID.fromString(followerId), UUID.fromString(followeeId),
                "NEW_FOLLOWER", "đã bắt đầu theo dõi bạn", "/users/" + followerId);
    }
}
