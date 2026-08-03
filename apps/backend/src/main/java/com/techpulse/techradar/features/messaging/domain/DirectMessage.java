package com.techpulse.techradar.features.messaging.domain;

import java.time.LocalDateTime;
import java.util.List;

public record DirectMessage(
        String id,
        String conversationId,
        String senderId,
        String content,
        LocalDateTime createdAt,
        boolean read,
        MessageAttachment attachment,
        List<MessageReactionSummary> reactions
) {
}
