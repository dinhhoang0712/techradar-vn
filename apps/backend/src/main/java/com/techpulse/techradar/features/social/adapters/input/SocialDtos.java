package com.techpulse.techradar.features.social.adapters.input;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.PostComment;
import com.techpulse.techradar.features.social.domain.ProfileSummary;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;

public class SocialDtos {

    @Value
    @Builder
    public static class UserSummaryResponse {
        String id;
        String fullName;
        String avatarUrl;

        public static UserSummaryResponse from(UserSummary u) {
            return UserSummaryResponse.builder()
                    .id(u.id())
                    .fullName(u.fullName())
                    .avatarUrl(u.avatarUrl())
                    .build();
        }
    }

    @Value
    @Builder
    public static class FeedPostResponse {
        String id;
        UserSummaryResponse author;
        String content;
        LocalDateTime createdAt;
        long likeCount;
        long commentCount;
        boolean likedByMe;

        public static FeedPostResponse from(FeedPost p) {
            return FeedPostResponse.builder()
                    .id(p.id())
                    .author(UserSummaryResponse.from(p.author()))
                    .content(p.content())
                    .createdAt(p.createdAt())
                    .likeCount(p.likeCount())
                    .commentCount(p.commentCount())
                    .likedByMe(p.likedByMe())
                    .build();
        }
    }

    @Value
    @Builder
    public static class CommentResponse {
        String id;
        UserSummaryResponse author;
        String content;
        LocalDateTime createdAt;

        public static CommentResponse from(PostComment c) {
            return CommentResponse.builder()
                    .id(c.id())
                    .author(UserSummaryResponse.from(c.author()))
                    .content(c.content())
                    .createdAt(c.createdAt())
                    .build();
        }
    }

    @Value
    @Builder
    public static class ProfileSummaryResponse {
        String id;
        String fullName;
        String avatarUrl;
        String bio;
        String jobRole;
        String location;
        long followerCount;
        long followingCount;
        long postCount;
        @JsonProperty("is_following")
        boolean following;

        public static ProfileSummaryResponse from(ProfileSummary p) {
            return ProfileSummaryResponse.builder()
                    .id(p.id())
                    .fullName(p.fullName())
                    .avatarUrl(p.avatarUrl())
                    .bio(p.bio())
                    .jobRole(p.jobRole())
                    .location(p.location())
                    .followerCount(p.followerCount())
                    .followingCount(p.followingCount())
                    .postCount(p.postCount())
                    .following(p.isFollowing())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddCommentRequest {
        private String content;
    }
}
