package com.techpulse.techradar.features.graph.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Filters for graph exploration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphFilter {
    private List<String> locations;
    private Integer minSalary;
    private Integer maxSalary;
    private String sentiment;
    private List<String> nodeTypes;
    private Integer maxDepth;
}
