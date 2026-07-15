package com.techpulse.techradar.features.messaging.domain;

import java.time.LocalDateTime;

public record ConversationSummary(
        String id,
        UserRef otherUser,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        String lastMessageSenderId,
        long unreadCount
) {
}
