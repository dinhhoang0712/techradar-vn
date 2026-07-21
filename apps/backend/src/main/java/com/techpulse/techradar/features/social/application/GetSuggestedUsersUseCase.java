package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** "Who to follow" — users the viewer doesn't already follow, ranked by follower count. */
@Component
@RequiredArgsConstructor
public class GetSuggestedUsersUseCase {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final FollowRepository followRepository;

    public Flux<UserSummary> execute(String viewerId, int limit) {
        int effectiveLimit = PageRequest.of(0, limit, DEFAULT_LIMIT, MAX_LIMIT).size();

        return followRepository.suggested(UUID.fromString(viewerId), effectiveLimit)
                .map(row -> new UserSummary(row.id().toString(), row.fullName(), row.avatarUrl()));
    }
}
