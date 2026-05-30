package com.techpulse.techradar.features.kafka.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExtractedJob {
    private String messageType;
    private String sourcePlatform;
    private OffsetDateTime crawledAt;
    private OffsetDateTime extractedAt;
    private ExtractedJobData data;
}
