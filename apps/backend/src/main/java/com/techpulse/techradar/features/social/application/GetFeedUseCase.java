package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.FeedScope;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
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
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);
        String hashtagFilter = (hashtag == null || hashtag.isBlank()) ? null : hashtag.trim().toLowerCase();
        UUID viewerId = UUID.fromString(userId);

        Flux<PostRepository.FeedRow> rows = FeedScope.fromParam(scope) == FeedScope.EXPLORE
                ? postRepository.findExplore(viewerId, hashtagFilter, pageRequest.size(), pageRequest.offset())
                : postRepository.findFeed(viewerId, hashtagFilter, pageRequest.size(), pageRequest.offset());

        return rows.map(FeedMapper::toFeedPost);
    }
}
