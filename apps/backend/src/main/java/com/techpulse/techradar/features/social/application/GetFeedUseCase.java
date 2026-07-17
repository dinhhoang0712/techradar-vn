package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.ports.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Posts by the current user and everyone they follow (default), or every public post ("explore"). */
@Component
@RequiredArgsConstructor
public class GetFeedUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostRepository postRepository;

    public Flux<FeedPost> execute(String userId, String scope, String hashtag, int page, int size) {
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;
        String hashtagFilter = (hashtag == null || hashtag.isBlank()) ? null : hashtag.trim().toLowerCase();
        UUID viewerId = UUID.fromString(userId);

        Flux<PostRepository.FeedRow> rows = "explore".equals(scope)
                ? postRepository.findExplore(viewerId, hashtagFilter, effectiveSize, offset)
                : postRepository.findFeed(viewerId, hashtagFilter, effectiveSize, offset);

        return rows.map(FeedMapper::toFeedPost);
    }
}
