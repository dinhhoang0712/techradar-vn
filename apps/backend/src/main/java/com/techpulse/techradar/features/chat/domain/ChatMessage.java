package com.techpulse.techradar.features.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a chat message that can be persisted by the backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private UUID id;

    private UUID sessionId;

    private String role;

    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private String finishReason;

    private Instant createdAt;
}
