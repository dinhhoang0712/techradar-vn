package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.ports.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Posts by the current user and everyone they follow, newest first. */
@Component
@RequiredArgsConstructor
public class GetFeedUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostRepository postRepository;

    public Flux<FeedPost> execute(String userId, int page, int size) {
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;

        return postRepository.findFeed(UUID.fromString(userId), effectiveSize, offset)
                .map(FeedMapper::toFeedPost);
    }
}
