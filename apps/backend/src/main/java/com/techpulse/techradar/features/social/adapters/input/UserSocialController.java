package com.techpulse.techradar.features.social.adapters.input;

import com.techpulse.techradar.features.social.application.GetProfileSummaryUseCase;
import com.techpulse.techradar.features.social.application.GetSuggestedUsersUseCase;
import com.techpulse.techradar.features.social.application.GetUserPostsUseCase;
import com.techpulse.techradar.features.social.application.SearchUsersUseCase;
import com.techpulse.techradar.features.social.application.ToggleFollowUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Public user profile + follow graph for the social feed.
 */
@Tag(name = "Social", description = "Public profile, follow/unfollow, suggested users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserSocialController {

    private final GetProfileSummaryUseCase getProfileSummaryUseCase;
    private final GetUserPostsUseCase getUserPostsUseCase;
    private final ToggleFollowUseCase toggleFollowUseCase;
    private final GetSuggestedUsersUseCase getSuggestedUsersUseCase;
    private final SearchUsersUseCase searchUsersUseCase;

    @Operation(summary = "Public profile summary (bio, follower/following counts, is-following)")
    @GetMapping("/{id}/profile-summary")
    public Mono<ResponseEntity<ApiResponse<SocialDtos.ProfileSummaryResponse>>> profileSummary(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(viewerId -> getProfileSummaryUseCase.execute(id, viewerId))
                .map(SocialDtos.ProfileSummaryResponse::from)
                .map(summary -> ResponseEntity.ok(ApiResponse.success(summary, "Profile summary")));
    }

    @Operation(summary = "A user's own posts (their profile feed)")
    @GetMapping("/{id}/posts")
    public Mono<ResponseEntity<ApiResponse<List<SocialDtos.FeedPostResponse>>>> posts(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(viewerId -> getUserPostsUseCase.execute(id, viewerId, page, size))
                .map(SocialDtos.FeedPostResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "User posts")));
    }

    @Operation(summary = "Follow a user")
    @PostMapping("/{id}/follow")
    public Mono<ResponseEntity<ApiResponse<Void>>> follow(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(viewerId -> toggleFollowUseCase.follow(viewerId, id))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Followed")));
    }

    @Operation(summary = "Unfollow a user")
    @DeleteMapping("/{id}/follow")
    public Mono<ResponseEntity<ApiResponse<Void>>> unfollow(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(viewerId -> toggleFollowUseCase.unfollow(viewerId, id))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Unfollowed")));
    }

    @Operation(summary = "Suggested users to follow, ranked by follower count")
    @GetMapping("/suggested")
    public Mono<ResponseEntity<ApiResponse<List<SocialDtos.UserSummaryResponse>>>> suggested(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(viewerId -> getSuggestedUsersUseCase.execute(viewerId, limit))
                .map(SocialDtos.UserSummaryResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Suggested users")));
    }

    @Operation(summary = "Search users by (partial) full name — backs the @mention picker")
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<SocialDtos.UserSummaryResponse>>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(viewerId -> searchUsersUseCase.execute(viewerId, q, limit))
                .map(SocialDtos.UserSummaryResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Search results")));
    }
}
