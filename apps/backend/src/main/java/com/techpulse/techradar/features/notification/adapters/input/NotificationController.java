package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * In-app notification endpoints. All require an authenticated user; each query is scoped to the
 * caller's id from the JWT principal.
 */
@Tag(name = "Notifications", description = "In-app notifications (list, read, realtime stream)")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List the current user's notifications (newest first)")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<NotificationView>>>> list() {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> notificationService.list(userId, 50))
                .map(NotificationView::from)
                .collectList()
                .map(items -> ResponseEntity.ok(ApiResponse.success(items, "Notifications retrieved")));
    }

    @Operation(summary = "Count unread notifications")
    @GetMapping("/unread-count")
    public Mono<ResponseEntity<ApiResponse<Long>>> unreadCount() {
        return SecurityUtils.currentUserId()
                .flatMap(notificationService::unreadCount)
                .map(count -> ResponseEntity.ok(ApiResponse.success(count, "Unread count retrieved")));
    }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    public Mono<ResponseEntity<ApiResponse<Void>>> markRead(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> notificationService.markRead(id, userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Marked as read")));
    }

    @Operation(summary = "Mark all notifications as read")
    @PostMapping("/read-all")
    public Mono<ResponseEntity<ApiResponse<Void>>> markAllRead() {
        return SecurityUtils.currentUserId()
                .flatMap(notificationService::markAllRead)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "All marked as read")));
    }

    @Operation(summary = "Realtime notification stream (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationView>> stream() {
        Flux<ServerSentEvent<NotificationView>> events = SecurityUtils.currentUserId()
                .flatMapMany(notificationService::streamFor)
                .map(n -> ServerSentEvent.builder(NotificationView.from(n))
                        .event("notification")
                        .build());
        // Heartbeat keeps the connection alive through proxies that time out idle streams.
        Flux<ServerSentEvent<NotificationView>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(i -> ServerSentEvent.<NotificationView>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }
}
