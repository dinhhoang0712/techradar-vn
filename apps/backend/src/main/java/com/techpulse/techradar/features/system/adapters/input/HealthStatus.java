package com.techpulse.techradar.features.system.adapters.input;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@Builder
public class HealthStatus {
    private String status;
    private String version;
    private long timestamp;
}