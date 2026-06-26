package com.techpulse.techradar.features.notification.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An in-app notification delivered to a single user.
 */
@Data
@Builder
public class Notification {
    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;
}
