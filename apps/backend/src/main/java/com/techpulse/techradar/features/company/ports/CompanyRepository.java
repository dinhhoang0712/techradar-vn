package com.techpulse.techradar.features.company.ports;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import reactor.core.publisher.Flux;

import java.util.List;

public interface CompanyRepository {

    /**
     * Every company that has at least one required-skill signal via its job postings, with its
     * inferred tech stack (distinct Technology/Skill names required across its Job postings).
     */
    Flux<CompanyRaw> findAllWithTechStack();

    /**
     * Articles mentioning this company (Article-[:MENTIONS]->Company), most recent first.
     */
    Flux<CompanyMention> findMentions(String companyId, int limit);

    record CompanyRaw(
            String id,
            String name,
            String location,
            List<String> techStack,
            int jobCount,
            String industry,
            String size
    ) {
    }
}
