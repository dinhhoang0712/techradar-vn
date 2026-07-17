package com.techpulse.techradar.features.social.domain;

import java.time.LocalDateTime;

public record PostComment(
        String id,
        UserSummary author,
        String content,
        String parentId,
        LocalDateTime createdAt
) {
}
