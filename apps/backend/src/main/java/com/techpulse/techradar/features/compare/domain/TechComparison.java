package com.techpulse.techradar.features.compare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Technology comparison metric.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechComparison {
    private String technology1;
    private String technology2;
    private double growthRate1;
    private double growthRate2;
    private int jobCount1;
    private int jobCount2;
    private int articleCount1;
    private int articleCount2;
    private double comparisonScore;
}
