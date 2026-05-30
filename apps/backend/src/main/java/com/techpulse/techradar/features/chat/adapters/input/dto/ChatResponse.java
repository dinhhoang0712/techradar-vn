package com.techpulse.techradar.features.chat.adapters.input.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chat response payload from RAG service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatResponse {
    private String answer;
    private String sessionId;
    private List<SourceItem> sources;
    private List<String> entities;
    private List<String> jobTitles;
    private String query;
}
