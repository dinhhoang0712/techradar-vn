package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.application.SendAdminNotificationUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin-triggered notifications: send to one user, or broadcast to every active user.
 */
@Tag(name = "Admin", description = "Admin-triggered notifications (targeted or broadcast)")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final SendAdminNotificationUseCase sendAdminNotificationUseCase;

    @Operation(summary = "Send a notification to one user, or broadcast to all active users when userId is omitted")
    @PostMapping
    @PreAuthorize("hasAuthority('notification:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> send(@RequestBody SendNotificationRequest request) {
        boolean isBroadcast = request.getUserId() == null || request.getUserId().isBlank();
        return sendAdminNotificationUseCase.execute(request.getTitle(), request.getBody(), request.getLink(), request.getUserId())
                .map(count -> ResponseEntity.ok(ApiResponse.success(
                        Map.<String, Object>of("recipients", count),
                        isBroadcast ? "Đã gửi thông báo tới " + count + " người dùng" : "Đã gửi thông báo")))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "INVALID_REQUEST"))));
    }

    @Data
    public static class SendNotificationRequest {
        private String title;
        private String body;
        private String link;
        /** Null/blank = broadcast to every active user. */
        private String userId;
    }
}
