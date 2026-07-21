package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
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
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);

        return postRepository.findByUser(UUID.fromString(targetUserId), UUID.fromString(viewerId),
                        pageRequest.size(), pageRequest.offset())
                .map(FeedMapper::toFeedPost);
    }
}
