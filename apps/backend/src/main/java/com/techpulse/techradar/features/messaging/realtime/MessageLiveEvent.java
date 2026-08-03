package com.techpulse.techradar.features.messaging.realtime;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;

import java.util.List;

/**
 * Wire format for a live messaging event, relayed over Redis Pub/Sub and delivered to SSE
 * subscribers — mirrors {@link com.techpulse.techradar.features.social.realtime.FeedEvent}'s
 * flat-record-with-discriminator shape (a plain enum tag plus nullable per-variant fields), which
 * plays nicely with plain Jackson serialization and needs no polymorphic type annotations.
 */
public record MessageLiveEvent(
        Type type,
        DirectMessage message,
        String conversationId,
        String messageId,
        List<MessageReactionSummary> reactions
) {
    public enum Type {
        NEW_MESSAGE, REACTIONS_CHANGED
    }

    public static MessageLiveEvent newMessage(DirectMessage message) {
        return new MessageLiveEvent(Type.NEW_MESSAGE, message, null, null, null);
    }

    public static MessageLiveEvent reactionsChanged(String conversationId, String messageId, List<MessageReactionSummary> reactions) {
        return new MessageLiveEvent(Type.REACTIONS_CHANGED, null, conversationId, messageId, reactions);
    }
}
