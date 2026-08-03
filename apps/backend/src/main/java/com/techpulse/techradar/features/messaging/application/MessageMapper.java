package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.domain.MessageAttachment;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;

import java.util.List;

final class MessageMapper {

    private MessageMapper() {
    }

    /** Reactions are never known from the message row alone — callers that have them attach separately. */
    static DirectMessage toDomain(MessageRepository.MessageRow row) {
        return new DirectMessage(
                row.id().toString(),
                row.conversationId().toString(),
                row.senderId().toString(),
                row.content(),
                row.createdAt(),
                row.readAt() != null,
                toAttachment(row),
                List.of()
        );
    }

    private static MessageAttachment toAttachment(MessageRepository.MessageRow row) {
        if (row.attachmentContentType() == null) {
            return null;
        }
        return new MessageAttachment(row.attachmentContentType(), row.attachmentFilename(), row.attachmentSize());
    }
}
