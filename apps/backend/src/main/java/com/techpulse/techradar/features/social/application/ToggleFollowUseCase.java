package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ToggleFollowUseCase {

    private final FollowRepository followRepository;

    public Mono<Void> follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            return Mono.error(new AppException("Cannot follow yourself", 400, "INVALID_FOLLOW"));
        }
        return followRepository.follow(UUID.fromString(followerId), UUID.fromString(followeeId));
    }

    public Mono<Void> unfollow(String followerId, String followeeId) {
        return followRepository.unfollow(UUID.fromString(followerId), UUID.fromString(followeeId));
    }
}
