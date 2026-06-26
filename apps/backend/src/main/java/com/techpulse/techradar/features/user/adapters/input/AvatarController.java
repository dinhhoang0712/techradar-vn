package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.user.application.AvatarService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Avatar upload (auth, base64) + public serving of the stored image.
 */
@Tag(name = "User", description = "Avatar upload/serve")
@RestController
@RequestMapping("/user/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @Operation(summary = "Upload current user's avatar (base64)")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> upload(@RequestBody AvatarRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> avatarService.upload(userId, request.getContentType(), request.getDataBase64()))
                .map(url -> ResponseEntity.ok(
                        ApiResponse.success(Map.of("avatar_url", url), "Avatar updated")));
    }

    @Operation(summary = "Serve a user's avatar image (public)")
    @GetMapping("/{userId}")
    public Mono<ResponseEntity<byte[]>> serve(@PathVariable String userId) {
        return avatarService.get(userId)
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                avatar.contentType() != null ? avatar.contentType() : "image/png"))
                        .header("X-Content-Type-Options", "nosniff")            // don't let browsers sniff a script type
                        .header("Content-Disposition", "inline; filename=\"avatar\"")
                        .body(avatar.data()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvatarRequest {
        private String contentType;   // e.g. image/png
        private String dataBase64;    // raw base64 or data URL
    }
}
