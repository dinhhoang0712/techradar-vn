package com.techpulse.techradar.features.company.ports;

import reactor.core.publisher.Flux;

import java.util.List;

public interface CompanyRepository {

    /**
     * Every company that has at least one required-skill signal via its job postings, with its
     * inferred tech stack (distinct Technology/Skill names required across its Job postings).
     */
    Flux<CompanyRaw> findAllWithTechStack();

    record CompanyRaw(
            String id,
            String name,
            String location,
            List<String> techStack,
            int jobCount
    ) {
    }
}
