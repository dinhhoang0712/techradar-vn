package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.SimilarCompany;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Companies ranked by Jaccard similarity of their inferred tech stack against a target company.
 */
@Component
@RequiredArgsConstructor
public class GetSimilarCompaniesUseCase {

    private final CompanyRepository companyRepository;

    public Mono<List<SimilarCompany>> execute(String companyId, int limit) {
        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 100);

        return companyRepository.findAllWithTechStack()
                .collectList()
                .flatMap(all -> {
                    var target = all.stream()
                            .filter(c -> c.id().equals(companyId))
                            .findFirst();
                    if (target.isEmpty()) {
                        return Mono.error(new NotFoundException("Company not found: " + companyId));
                    }

                    Set<String> targetLower = lowerSet(target.get().techStack());
                    List<SimilarCompany> ranked = all.stream()
                            .filter(c -> !c.id().equals(companyId))
                            .map(c -> toSimilarCompany(c, targetLower))
                            .filter(s -> s.score() > 0)
                            .sorted((a, b) -> Double.compare(b.score(), a.score()))
                            .limit(effectiveLimit)
                            .toList();
                    return Mono.just(ranked);
                });
    }

    private SimilarCompany toSimilarCompany(CompanyRepository.CompanyRaw candidate, Set<String> targetLower) {
        Set<String> candidateLower = lowerSet(candidate.techStack());

        List<String> shared = new ArrayList<>();
        for (String tech : candidate.techStack()) {
            if (targetLower.contains(tech.toLowerCase(Locale.ROOT))) {
                shared.add(tech);
            }
        }

        Set<String> union = new LinkedHashSet<>(targetLower);
        union.addAll(candidateLower);
        double jaccard = union.isEmpty() ? 0.0 : (double) shared.size() / union.size();

        return new SimilarCompany(
                candidate.id(),
                CompanyNames.clean(candidate.name()),
                candidate.location(),
                shared,
                jaccard
        );
    }

    private static Set<String> lowerSet(List<String> names) {
        return names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
