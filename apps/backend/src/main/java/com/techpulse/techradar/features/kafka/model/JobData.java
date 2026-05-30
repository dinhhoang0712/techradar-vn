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
public class JobData {
    private String jobTitle;
    private String companyName;
    private String location;
    private String salary;
    private String level;
    private String description;
    private String requirement;
    private String benefit;
    private List<String> skills;
    private String sourceUrl;
    private String postedDate;
}
