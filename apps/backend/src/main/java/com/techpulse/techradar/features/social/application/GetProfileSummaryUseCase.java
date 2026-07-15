package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.ProfileSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetProfileSummaryUseCase {

    private final FollowRepository followRepository;
    private final PostRepository postRepository;

    public Mono<ProfileSummary> execute(String targetUserId, String viewerId) {
        UUID targetUuid = UUID.fromString(targetUserId);
        UUID viewerUuid = UUID.fromString(viewerId);

        return followRepository.findProfileBasics(targetUuid)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + targetUserId)))
                .flatMap(basics -> Mono.zip(
                        followRepository.followerCount(targetUuid),
                        followRepository.followingCount(targetUuid),
                        postRepository.countByUser(targetUuid),
                        targetUuid.equals(viewerUuid) ? Mono.just(false) : followRepository.isFollowing(viewerUuid, targetUuid)
                ).map(counts -> new ProfileSummary(
                        targetUserId,
                        basics.fullName(),
                        basics.avatarUrl(),
                        basics.bio(),
                        basics.jobRole(),
                        basics.location(),
                        counts.getT1(),
                        counts.getT2(),
                        counts.getT3(),
                        counts.getT4()
                )));
    }
}
