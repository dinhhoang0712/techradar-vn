package com.techpulse.techradar.features.chat.adapters.input.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat session summary item for the session-history sidebar.
 * Serialized as snake_case: {@code session_id}, {@code title}, {@code created_at}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionItem {
    private UUID sessionId;
    private String title;
    private Instant createdAt;
}
