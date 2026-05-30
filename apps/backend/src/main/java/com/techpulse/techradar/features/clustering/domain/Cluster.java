package com.techpulse.techradar.features.clustering.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Technology cluster domain entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cluster {
    private String clusterId;
    private String name;
    private String description;
    private List<String> technologies;
    private int size;
    private double score;
}
