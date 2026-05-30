package com.techpulse.techradar.features.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Entities {
    @JsonProperty("TECH")
    private List<String> tech;

    @JsonProperty("ORG")
    private List<String> org;

    @JsonProperty("LOC")
    private List<String> loc;

    @JsonProperty("DATE")
    private List<String> date;

    @JsonProperty("JOB_ROLE")
    private List<String> jobRole;

    @JsonProperty("SALARY")
    private List<String> salary;
}
