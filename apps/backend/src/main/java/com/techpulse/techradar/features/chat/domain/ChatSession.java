package com.techpulse.techradar.features.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a chat session tracked by the backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    private UUID id;

    private UUID userId;

    private String title;

    private String modelUsed;

    private String systemPrompt;

    private Instant createdAt;

    private Instant updatedAt;
}
