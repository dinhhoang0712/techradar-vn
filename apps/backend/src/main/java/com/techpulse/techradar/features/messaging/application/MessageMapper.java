package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;

final class MessageMapper {

    private MessageMapper() {
    }

    static DirectMessage toDomain(MessageRepository.MessageRow row) {
        return new DirectMessage(
                row.id().toString(),
                row.conversationId().toString(),
                row.senderId().toString(),
                row.content(),
                row.createdAt(),
                row.readAt() != null
        );
    }
}
