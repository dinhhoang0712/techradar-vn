package com.techpulse.techradar.features.radar.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Top technologies radar snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechRadar {
    private LocalDate snapshotDate;
    private List<RadarTrend> topTechnologies;
    private String period;
}
