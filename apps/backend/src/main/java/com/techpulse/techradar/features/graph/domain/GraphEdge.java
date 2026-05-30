package com.techpulse.techradar.features.graph.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Graph edge representing relationships.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {
    private String id;
    private String source;
    private String target;
    private String type;
    private java.util.Map<String, Object> properties;
}
