package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain event published to Kafka ({@code job.match.alerts}) the first time a job posting is
 * written to the graph (see {@code KafkaNeo4jWriterService}). Serialized snake_case by the shared
 * Jackson {@code ObjectMapper} (e.g. {@code job_title}, {@code company_name}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchEvent {
    private String jobTitle;
    private String companyName;
    private List<String> technologies;
    private String sourceUrl;
}
