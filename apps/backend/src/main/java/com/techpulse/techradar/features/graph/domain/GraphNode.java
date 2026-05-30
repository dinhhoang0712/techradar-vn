package com.techpulse.techradar.features.graph.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Graph node representing entities in knowledge graph.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNode {
    private String id;
    private String label;
    private String type;
    private String name;
    private java.util.Map<String, Object> properties;
}
