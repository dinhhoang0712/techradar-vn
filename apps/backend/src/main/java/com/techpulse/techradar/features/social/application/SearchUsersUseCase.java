package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Backs the @mention picker — search users by (partial) full name. */
@Component
@RequiredArgsConstructor
public class SearchUsersUseCase {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 25;

    private final FollowRepository followRepository;

    public Flux<UserSummary> execute(String viewerId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return Flux.empty(); // don't let an empty pattern mean "everyone"
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return followRepository.searchByName(UUID.fromString(viewerId), query.trim(), effectiveLimit)
                .map(row -> new UserSummary(row.id().toString(), row.fullName(), row.avatarUrl()));
    }
}
