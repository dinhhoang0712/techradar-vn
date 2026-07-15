package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Companies with an inferred tech-stack fingerprint, ranked by job count.
 */
@Component
@RequiredArgsConstructor
public class GetCompaniesUseCase {

    private final CompanyRepository companyRepository;

    public Flux<CompanyProfile> execute() {
        return companyRepository.findAllWithTechStack()
                .map(raw -> new CompanyProfile(
                        raw.id(),
                        CompanyNames.clean(raw.name()),
                        raw.location(),
                        raw.techStack(),
                        raw.jobCount()
                ));
    }
}
