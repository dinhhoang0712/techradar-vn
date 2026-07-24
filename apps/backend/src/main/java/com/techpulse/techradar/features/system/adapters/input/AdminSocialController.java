package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.adapters.input.AdminSocialDtos.CommentView;
import com.techpulse.techradar.features.system.adapters.input.AdminSocialDtos.PostView;
import com.techpulse.techradar.features.system.adapters.input.AdminSocialDtos.ReportView;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.features.system.application.SocialModerationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.paging.PageRequest;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin moderation over the social feed (view/delete any post or comment).
 */
@Tag(name = "Admin", description = "Admin social feed moderation")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSocialController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final SocialModerationService moderationService;
    private final AuditLogService auditLogService;

    @Operation(summary = "List all posts for moderation")
    @GetMapping("/posts")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<List<PostView>>>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);
        return moderationService.listPosts(pageRequest.size(), pageRequest.offset())
                .map(PostView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Posts")));
    }

    @Operation(summary = "Delete any post (moderation)")
    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deletePost(@PathVariable String id) {
        return moderationService.deletePost(id)
                .then(auditLogService.record("POST_DELETE", "post", id, null))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Post deleted")));
    }

    @Operation(summary = "List comments on a post for moderation")
    @GetMapping("/posts/{id}/comments")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<List<CommentView>>>> listComments(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);
        return moderationService.listComments(id, pageRequest.size(), pageRequest.offset())
                .map(CommentView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Comments")));
    }

    @Operation(summary = "Delete any comment (moderation)")
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteComment(@PathVariable String id) {
        return moderationService.deleteComment(id)
                .then(auditLogService.record("COMMENT_DELETE", "comment", id, null))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Comment deleted")));
    }

    @Operation(summary = "List pending content reports (moderation queue)")
    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<List<ReportView>>>> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);
        return moderationService.listPendingReports(pageRequest.size(), pageRequest.offset())
                .map(ReportView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Pending reports")));
    }

    @Operation(summary = "Dismiss a pending report (no violation found)")
    @PostMapping("/reports/{id}/dismiss")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<Void>>> dismissReport(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(adminId -> moderationService.dismissReport(id, adminId))
                .then(auditLogService.record("REPORT_DISMISS", "report", id, null))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Report dismissed")));
    }

    @Operation(summary = "Get (or, with force=true, regenerate) the AI moderation suggestion for a report")
    @PostMapping("/reports/{id}/ai-suggestion")
    @PreAuthorize("hasAuthority('social:moderate')")
    public Mono<ResponseEntity<ApiResponse<ReportView>>> getAiSuggestion(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return moderationService.getAiSuggestion(id, force)
                .map(ReportView::from)
                .map(view -> ResponseEntity.ok(ApiResponse.success(view, "AI suggestion")));
    }
}
