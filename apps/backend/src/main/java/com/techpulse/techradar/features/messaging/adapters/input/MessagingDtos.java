package com.techpulse.techradar.features.messaging.adapters.input;

import com.techpulse.techradar.features.messaging.domain.ConversationSummary;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.domain.MessageAttachment;
import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;
import com.techpulse.techradar.features.messaging.domain.UserRef;
import com.techpulse.techradar.features.messaging.realtime.MessageLiveEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

public class MessagingDtos {

    @Value
    @Builder
    public static class UserRefResponse {
        String id;
        String fullName;
        String avatarUrl;

        public static UserRefResponse from(UserRef u) {
            return UserRefResponse.builder().id(u.id()).fullName(u.fullName()).avatarUrl(u.avatarUrl()).build();
        }
    }

    @Value
    @Builder
    public static class ConversationResponse {
        String id;
        UserRefResponse otherUser;
        String lastMessageContent;
        LocalDateTime lastMessageAt;
        String lastMessageSenderId;
        long unreadCount;

        public static ConversationResponse from(ConversationSummary c) {
            return ConversationResponse.builder()
                    .id(c.id())
                    .otherUser(UserRefResponse.from(c.otherUser()))
                    .lastMessageContent(c.lastMessageContent())
                    .lastMessageAt(c.lastMessageAt())
                    .lastMessageSenderId(c.lastMessageSenderId())
                    .unreadCount(c.unreadCount())
                    .build();
        }
    }

    @Value
    @Builder
    public static class AttachmentResponse {
        String contentType;
        String filename;
        int size;
        String url;

        public static AttachmentResponse from(String conversationId, String messageId, MessageAttachment a) {
            if (a == null) {
                return null;
            }
            return AttachmentResponse.builder()
                    .contentType(a.contentType())
                    .filename(a.filename())
                    .size(a.size())
                    .url("/conversations/" + conversationId + "/messages/" + messageId + "/attachment")
                    .build();
        }
    }

    @Value
    @Builder
    public static class MessageReactionResponse {
        String emoji;
        int count;
        boolean reactedByMe;

        public static MessageReactionResponse from(MessageReactionSummary r) {
            return MessageReactionResponse.builder().emoji(r.emoji()).count(r.count()).reactedByMe(r.reactedByMe()).build();
        }
    }

    @Value
    @Builder
    public static class DirectMessageResponse {
        String id;
        String conversationId;
        String senderId;
        String content;
        LocalDateTime createdAt;
        boolean read;
        AttachmentResponse attachment;
        List<MessageReactionResponse> reactions;

        public static DirectMessageResponse from(DirectMessage m) {
            return DirectMessageResponse.builder()
                    .id(m.id())
                    .conversationId(m.conversationId())
                    .senderId(m.senderId())
                    .content(m.content())
                    .createdAt(m.createdAt())
                    .read(m.read())
                    .attachment(AttachmentResponse.from(m.conversationId(), m.id(), m.attachment()))
                    .reactions(m.reactions().stream().map(MessageReactionResponse::from).toList())
                    .build();
        }
    }

    @Value
    @Builder
    public static class MessageLiveEventResponse {
        String type;
        DirectMessageResponse message;
        String conversationId;
        String messageId;
        List<MessageReactionResponse> reactions;

        public static MessageLiveEventResponse from(MessageLiveEvent e) {
            return switch (e.type()) {
                case NEW_MESSAGE -> MessageLiveEventResponse.builder()
                        .type("NEW_MESSAGE")
                        .message(DirectMessageResponse.from(e.message()))
                        .build();
                case REACTIONS_CHANGED -> MessageLiveEventResponse.builder()
                        .type("REACTIONS_CHANGED")
                        .conversationId(e.conversationId())
                        .messageId(e.messageId())
                        .reactions(e.reactions().stream().map(MessageReactionResponse::from).toList())
                        .build();
            };
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentInputDto {
        private String contentType;
        private String filename;
        private String dataBase64;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private String content;
        private AttachmentInputDto attachment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetReactionRequest {
        private String emoji;
    }
}
