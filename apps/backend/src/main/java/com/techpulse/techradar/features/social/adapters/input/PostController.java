package com.techpulse.techradar.features.social.adapters.input;

import com.techpulse.techradar.features.social.application.AddCommentUseCase;
import com.techpulse.techradar.features.social.application.CreatePostUseCase;
import com.techpulse.techradar.features.social.application.DeletePostUseCase;
import com.techpulse.techradar.features.social.application.GetCommentsUseCase;
import com.techpulse.techradar.features.social.application.GetFeedUseCase;
import com.techpulse.techradar.features.social.application.PostImageService;
import com.techpulse.techradar.features.social.application.ReportContentUseCase;
import com.techpulse.techradar.features.social.application.ToggleLikeUseCase;
import com.techpulse.techradar.features.social.realtime.FeedBroadcaster;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import com.techpulse.techradar.shared.sse.SseHeartbeat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Social feed — posts, likes, comments. Follows/profile live in {@link UserSocialController}.
 */
@Tag(name = "Social", description = "Posts, likes, comments and the feed")
@RestController
@RequiredArgsConstructor
public class PostController {

    private final CreatePostUseCase createPostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final GetFeedUseCase getFeedUseCase;
    private final ToggleLikeUseCase toggleLikeUseCase;
    private final GetCommentsUseCase getCommentsUseCase;
    private final AddCommentUseCase addCommentUseCase;
    private final ReportContentUseCase reportContentUseCase;
    private final PostImageService postImageService;
    private final FeedBroadcaster feedBroadcaster;

    @Operation(summary = "Feed: 'following' (self + followees, default) or 'explore' (every public post)")
    @GetMapping("/feed")
    public Mono<ResponseEntity<ApiResponse<List<SocialDtos.FeedPostResponse>>>> feed(
            @RequestParam(defaultValue = "following") String scope,
            @RequestParam(required = false) String hashtag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> getFeedUseCase.execute(userId, scope, hashtag, page, size))
                .map(SocialDtos.FeedPostResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Feed")));
    }

    @Operation(summary = "Realtime feed stream (SSE) — new posts, likes and comment counts")
    @GetMapping(value = "/feed/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SocialDtos.FeedEventResponse>> stream(
            @RequestParam(defaultValue = "following") String scope
    ) {
        Flux<ServerSentEvent<SocialDtos.FeedEventResponse>> events = SecurityUtils.currentUserId()
                .flatMapMany(userId -> feedBroadcaster.streamFor(userId, scope))
                .map(event -> ServerSentEvent.builder(SocialDtos.FeedEventResponse.from(event))
                        .event(event.type().name())
                        .build());
        return SseHeartbeat.merge(events);
    }

    @Operation(summary = "Create a post")
    @PostMapping("/posts")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> create(@RequestBody SocialDtos.CreatePostRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> createPostUseCase.execute(
                        userId, request.getContent(), request.getImages(),
                        request.getTaggedCompanyId(), request.getMentionedUserIds()))
                .map(postId -> ResponseEntity.ok(ApiResponse.success(Map.of("id", postId), "Post created")));
    }

    @Operation(summary = "Delete your own post")
    @DeleteMapping("/posts/{id}")
    public Mono<ResponseEntity<ApiResponse<Void>>> delete(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> deletePostUseCase.execute(id, userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Post deleted")));
    }

    @Operation(summary = "Serve a post image (public)")
    @GetMapping("/posts/{postId}/images/{imageId}")
    public Mono<ResponseEntity<byte[]>> image(@PathVariable String postId, @PathVariable String imageId) {
        return postImageService.get(UUID.fromString(imageId))
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                image.contentType() != null ? image.contentType() : "image/png"))
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Disposition", "inline; filename=\"post-image\"")
                        .body(image.data()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Like a post")
    @PostMapping("/posts/{id}/like")
    public Mono<ResponseEntity<ApiResponse<Void>>> like(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> toggleLikeUseCase.like(id, userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Liked")));
    }

    @Operation(summary = "Unlike a post")
    @DeleteMapping("/posts/{id}/like")
    public Mono<ResponseEntity<ApiResponse<Void>>> unlike(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> toggleLikeUseCase.unlike(id, userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Unliked")));
    }

    @Operation(summary = "List comments on a post (flat list; each item carries parent_id for client-side threading)")
    @GetMapping("/posts/{id}/comments")
    public Mono<ResponseEntity<ApiResponse<List<SocialDtos.CommentResponse>>>> comments(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return getCommentsUseCase.execute(id, page, size)
                .map(SocialDtos.CommentResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Comments")));
    }

    @Operation(summary = "Add a comment to a post (optionally a reply via parent_id)")
    @PostMapping("/posts/{id}/comments")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> addComment(
            @PathVariable String id,
            @RequestBody SocialDtos.AddCommentRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> addCommentUseCase.execute(
                        id, userId, request.getContent(), request.getParentId(), request.getMentionedUserIds()))
                .map(commentId -> ResponseEntity.ok(ApiResponse.success(Map.of("id", commentId), "Comment added")));
    }

    @Operation(summary = "Report a post for moderation")
    @PostMapping("/posts/{id}/report")
    public Mono<ResponseEntity<ApiResponse<Void>>> reportPost(
            @PathVariable String id,
            @RequestBody SocialDtos.ReportRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> reportContentUseCase.reportPost(id, userId, request.getReason()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Post reported")));
    }

    @Operation(summary = "Report a comment for moderation")
    @PostMapping("/comments/{id}/report")
    public Mono<ResponseEntity<ApiResponse<Void>>> reportComment(
            @PathVariable String id,
            @RequestBody SocialDtos.ReportRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> reportContentUseCase.reportComment(id, userId, request.getReason()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Comment reported")));
    }
}
