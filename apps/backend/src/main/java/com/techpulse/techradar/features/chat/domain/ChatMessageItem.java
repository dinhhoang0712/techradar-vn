package com.techpulse.techradar.features.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Chat message history item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageItem {
    private UUID id;
    private String role;
    private String content;
}
