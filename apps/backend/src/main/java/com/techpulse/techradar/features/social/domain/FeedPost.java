package com.techpulse.techradar.features.social.domain;

import java.time.LocalDateTime;
import java.util.List;

public record FeedPost(
        String id,
        UserSummary author,
        String content,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        List<String> imageUrls,
        List<String> hashtags,
        TaggedCompany taggedCompany
) {
    /** Denormalized snapshot of a company tagged on a post (no live FK — Company lives in Neo4j). */
    public record TaggedCompany(String id, String name, String location) {
    }
}
