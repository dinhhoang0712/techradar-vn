package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.PostRepository;

import java.util.List;

final class FeedMapper {

    private FeedMapper() {
    }

    static FeedPost toFeedPost(PostRepository.FeedRow row) {
        List<String> imageUrls = row.imageIds() == null ? List.of() : row.imageIds().stream()
                .map(imgId -> "/api/v1/posts/" + row.id() + "/images/" + imgId)
                .toList();
        List<String> hashtags = row.hashtags() == null ? List.of() : row.hashtags();
        FeedPost.TaggedCompany taggedCompany = row.taggedCompanyId() == null ? null
                : new FeedPost.TaggedCompany(row.taggedCompanyId(), row.taggedCompanyName(), row.taggedCompanyLocation());

        return new FeedPost(
                row.id().toString(),
                new UserSummary(row.authorId().toString(), row.authorName(), row.authorAvatarUrl()),
                row.content(),
                row.createdAt(),
                row.likeCount(),
                row.commentCount(),
                row.likedByMe(),
                imageUrls,
                hashtags,
                taggedCompany
        );
    }
}
