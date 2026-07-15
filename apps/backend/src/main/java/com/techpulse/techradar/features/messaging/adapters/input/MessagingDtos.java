package com.techpulse.techradar.features.messaging.adapters.input;

import com.techpulse.techradar.features.messaging.domain.ConversationSummary;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.domain.UserRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;

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
    public static class DirectMessageResponse {
        String id;
        String conversationId;
        String senderId;
        String content;
        LocalDateTime createdAt;
        boolean read;

        public static DirectMessageResponse from(DirectMessage m) {
            return DirectMessageResponse.builder()
                    .id(m.id())
                    .conversationId(m.conversationId())
                    .senderId(m.senderId())
                    .content(m.content())
                    .createdAt(m.createdAt())
                    .read(m.read())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private String content;
    }
}
