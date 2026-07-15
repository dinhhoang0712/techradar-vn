package com.techpulse.techradar.features.social.domain;

public record ProfileSummary(
        String id,
        String fullName,
        String avatarUrl,
        String bio,
        String jobRole,
        String location,
        long followerCount,
        long followingCount,
        long postCount,
        boolean isFollowing
) {
}
