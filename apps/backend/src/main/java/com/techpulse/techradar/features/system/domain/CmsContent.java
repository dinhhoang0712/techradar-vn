package com.techpulse.techradar.features.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A CMS content record (crawled report / job / keyword) managed in the admin CMS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsContent {
    private UUID id;
    private String title;
    private String type;          // Report | Job | Keyword
    private LocalDate contentDate;
    private String status;        // Published | Analyzed | Pending | Archived
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
