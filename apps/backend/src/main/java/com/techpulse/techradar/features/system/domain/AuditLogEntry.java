package com.techpulse.techradar.features.system.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One recorded admin mutation ("who did what, when"). Append-only.
 */
@Data
@Builder
public class AuditLogEntry {
    private UUID id;
    private UUID actorId;
    private String action;
    private String targetType;
    private String targetId;
    private String details;
    private LocalDateTime createdAt;
}
