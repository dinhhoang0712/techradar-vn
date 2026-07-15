package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.ports.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** A single user's own posts (their profile feed), as seen by the current viewer. */
@Component
@RequiredArgsConstructor
public class GetUserPostsUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostRepository postRepository;

    public Flux<FeedPost> execute(String targetUserId, String viewerId, int page, int size) {
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;

        return postRepository.findByUser(UUID.fromString(targetUserId), UUID.fromString(viewerId), effectiveSize, offset)
                .map(FeedMapper::toFeedPost);
    }
}
