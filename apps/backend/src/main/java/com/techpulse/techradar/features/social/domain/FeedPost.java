package com.techpulse.techradar.features.social.domain;

import java.time.LocalDateTime;

public record FeedPost(
        String id,
        UserSummary author,
        String content,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount,
        boolean likedByMe
) {
}
