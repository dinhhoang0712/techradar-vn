package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.domain.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Client-facing notification DTO (serialized snake_case, e.g. {@code created_at}).
 */
@Data
@Builder
public class NotificationView {
    private String id;
    private String type;
    private String title;
    private String body;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationView from(Notification n) {
        return NotificationView.builder()
                .id(n.getId() != null ? n.getId().toString() : null)
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
