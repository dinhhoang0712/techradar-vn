package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.application.SocialModerationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.Builder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin moderation over the social feed (view/delete any post or comment).
 */
@Tag(name = "Admin", description = "Admin social feed moderation")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSocialController {

    private final SocialModerationService moderationService;

    @Operation(summary = "List all posts for moderation")
    @GetMapping("/posts")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<PostView>>>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.listPosts(size, page * size)
                .map(PostView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Posts")));
    }

    @Operation(summary = "Delete any post (moderation)")
    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deletePost(@PathVariable String id) {
        return moderationService.deletePost(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Post deleted")));
    }

    @Operation(summary = "List comments on a post for moderation")
    @GetMapping("/posts/{id}/comments")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<CommentView>>>> listComments(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.listComments(id, size, page * size)
                .map(CommentView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Comments")));
    }

    @Operation(summary = "Delete any comment (moderation)")
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteComment(@PathVariable String id) {
        return moderationService.deleteComment(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Comment deleted")));
    }

    @Operation(summary = "List pending content reports (moderation queue)")
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<ReportView>>>> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.listPendingReports(size, page * size)
                .map(ReportView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Pending reports")));
    }

    @Operation(summary = "Dismiss a pending report (no violation found)")
    @PostMapping("/reports/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> dismissReport(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(adminId -> moderationService.dismissReport(id, adminId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Report dismissed")));
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

        static PostView from(PostRepository.FeedRow r) {
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

        static CommentView from(CommentRepository.CommentRow r) {
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

        static ReportView from(ReportRepository.ReportRow r) {
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
                    .build();
        }
    }
}
