package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.PostRepository;

final class FeedMapper {

    private FeedMapper() {
    }

    static FeedPost toFeedPost(PostRepository.FeedRow row) {
        return new FeedPost(
                row.id().toString(),
                new UserSummary(row.authorId().toString(), row.authorName(), row.authorAvatarUrl()),
                row.content(),
                row.createdAt(),
                row.likeCount(),
                row.commentCount(),
                row.likedByMe()
        );
    }
}
