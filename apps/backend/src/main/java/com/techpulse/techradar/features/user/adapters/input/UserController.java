package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.user.application.UserService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * User API controller.
 */
@Tag(name = "User", description = "Authenticated user endpoints")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile")
    @GetMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> getProfile() {
        return extractCurrentUserId()
                .flatMap(userService::getProfile)
                .map(UserProfileResponse::fromUser)
                .map(profile -> ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved")))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized", "UNAUTHORIZED"))));
    }

    @Operation(summary = "Update current user profile")
    @PutMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return extractCurrentUserId()
                .flatMap(userId -> userService.updateProfile(userId,
                        StringUtils.hasText(request.getEmail()) ? request.getEmail() : null,
                        StringUtils.hasText(request.getSubscriptionTier()) ? request.getSubscriptionTier() : null))
                .map(UserProfileResponse::fromUser)
                .map(updated -> ResponseEntity.ok(ApiResponse.success(updated, "Profile updated")))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized", "UNAUTHORIZED"))));
    }

    private Mono<String> extractCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Objects::nonNull)
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getName());
    }
}
