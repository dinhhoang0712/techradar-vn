package com.techpulse.techradar.features.company.domain;

import java.util.List;

/**
 * A company's tech stack, inferred from the technologies required by its job postings — see
 * Neo4jCompanyRepository for why this is preferred over Company-[:USES]->Technology.
 */
public record CompanyProfile(
        String id,
        String name,
        String location,
        List<String> techStack,
        int jobCount,
        String industry,
        String size
) {
}
