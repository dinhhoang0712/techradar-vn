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
import java.util.List;

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
    public static class TaggedCompanyResponse {
        String id;
        String name;
        String location;

        public static TaggedCompanyResponse from(FeedPost.TaggedCompany c) {
            if (c == null) {
                return null;
            }
            return TaggedCompanyResponse.builder()
                    .id(c.id())
                    .name(c.name())
                    .location(c.location())
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
        List<String> imageUrls;
        List<String> hashtags;
        TaggedCompanyResponse taggedCompany;

        public static FeedPostResponse from(FeedPost p) {
            return FeedPostResponse.builder()
                    .id(p.id())
                    .author(UserSummaryResponse.from(p.author()))
                    .content(p.content())
                    .createdAt(p.createdAt())
                    .likeCount(p.likeCount())
                    .commentCount(p.commentCount())
                    .likedByMe(p.likedByMe())
                    .imageUrls(p.imageUrls())
                    .hashtags(p.hashtags())
                    .taggedCompany(TaggedCompanyResponse.from(p.taggedCompany()))
                    .build();
        }
    }

    @Value
    @Builder
    public static class CommentResponse {
        String id;
        UserSummaryResponse author;
        String content;
        String parentId;
        LocalDateTime createdAt;

        public static CommentResponse from(PostComment c) {
            return CommentResponse.builder()
                    .id(c.id())
                    .author(UserSummaryResponse.from(c.author()))
                    .content(c.content())
                    .parentId(c.parentId())
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
    public static class ImageInput {
        private String contentType;   // e.g. image/png
        private String dataBase64;    // raw base64 or data URL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        private String content;
        private List<ImageInput> images;
        private String taggedCompanyId;
        private List<String> mentionedUserIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddCommentRequest {
        private String content;
        private String parentId;
        private List<String> mentionedUserIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportRequest {
        private String reason;
    }
}
