package com.techpulse.techradar.features.radar.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Radar trend domain entity - represents technology trend metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarTrend {
    private String technologyName;
    private LocalDate month;
    private int jobCount;
    private int articleCount;
    private double growthRate;
    private double yoyGrowth;
    private double momGrowth;
    private int ranking;
    private String category;
}
