package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Response shapes for {@link AdminSocialController}, kept out of the controller itself so
 * response-mapping logic is isolated and testable on its own — matching the convention used by
 * {@code SocialDtos} for the equivalent non-admin endpoints.
 */
public class AdminSocialDtos {

    private AdminSocialDtos() {
    }

    @Value
    @Builder
    public static class PostView {
        String id;
        String authorId;
        String authorName;
        String authorAvatarUrl;
        String content;
        LocalDateTime createdAt;
        long likeCount;
        long commentCount;

        public static PostView from(PostRepository.FeedRow r) {
            return PostView.builder()
                    .id(r.id().toString())
                    .authorId(r.authorId().toString())
                    .authorName(r.authorName())
                    .authorAvatarUrl(r.authorAvatarUrl())
                    .content(r.content())
                    .createdAt(r.createdAt())
                    .likeCount(r.likeCount())
                    .commentCount(r.commentCount())
                    .build();
        }
    }

    @Value
    @Builder
    public static class CommentView {
        String id;
        String authorId;
        String authorName;
        String authorAvatarUrl;
        String content;
        LocalDateTime createdAt;

        public static CommentView from(CommentRepository.CommentRow r) {
            return CommentView.builder()
                    .id(r.id().toString())
                    .authorId(r.authorId().toString())
                    .authorName(r.authorName())
                    .authorAvatarUrl(r.authorAvatarUrl())
                    .content(r.content())
                    .createdAt(r.createdAt())
                    .build();
        }
    }

    @Value
    @Builder
    public static class ReportView {
        String id;
        String reporterId;
        String reporterName;
        String postId;
        String commentId;
        String targetType;
        String targetContent;
        String targetAuthorName;
        String reason;
        String status;
        LocalDateTime createdAt;
        String aiSuggestedAction;
        String aiSuggestedReason;
        Double aiConfidence;
        LocalDateTime aiSuggestedAt;

        public static ReportView from(ReportRepository.ReportRow r) {
            return ReportView.builder()
                    .id(r.id().toString())
                    .reporterId(r.reporterId().toString())
                    .reporterName(r.reporterName())
                    .postId(r.postId() != null ? r.postId().toString() : null)
                    .commentId(r.commentId() != null ? r.commentId().toString() : null)
                    .targetType(r.postId() != null ? "POST" : "COMMENT")
                    .targetContent(r.targetContent())
                    .targetAuthorName(r.targetAuthorName())
                    .reason(r.reason())
                    .status(r.status())
                    .createdAt(r.createdAt())
                    .aiSuggestedAction(r.aiSuggestedAction())
                    .aiSuggestedReason(r.aiSuggestedReason())
                    .aiConfidence(r.aiConfidence())
                    .aiSuggestedAt(r.aiSuggestedAt())
                    .build();
        }
    }
}
