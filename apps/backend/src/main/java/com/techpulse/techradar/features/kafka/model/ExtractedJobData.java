package com.techpulse.techradar.features.kafka.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExtractedJobData {
    private JobInfo job;
    private CompanyInfo company;
    private List<String> skills;
    private List<String> technologies;
    private Entities entities;
}
