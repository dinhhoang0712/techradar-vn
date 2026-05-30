package com.techpulse.techradar.features.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Application settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSettings {
    private String key;
    private String value;
    private String description;
    private LocalDateTime updatedAt;
}
