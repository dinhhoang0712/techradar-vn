package com.techpulse.techradar.features.company.domain;

import java.util.List;

/**
 * Another company ranked by Jaccard similarity of tech stacks against a target company.
 */
public record SimilarCompany(
        String id,
        String name,
        String location,
        List<String> sharedTechs,
        double score
) {
}
