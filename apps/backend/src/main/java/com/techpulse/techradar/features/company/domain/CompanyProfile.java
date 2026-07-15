package com.techpulse.techradar.features.company.domain;

import java.util.List;

/**
 * A company's tech stack, inferred from the technologies required by its job postings
 * (Company.USES is never populated by any ingestion pipeline — see Neo4jCompanyRepository).
 */
public record CompanyProfile(
        String id,
        String name,
        String location,
        List<String> techStack,
        int jobCount
) {
}
