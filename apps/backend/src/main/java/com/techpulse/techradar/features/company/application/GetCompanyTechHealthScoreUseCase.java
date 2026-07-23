package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyTechHealthScore;
import com.techpulse.techradar.features.company.domain.CompanyTechHealthScoreCalculator;
import com.techpulse.techradar.features.kafka.ports.TechAliasResolver;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Company Tech Health Score — how much a company's inferred tech stack is trending up vs. down in
 * market demand, per {@link CompanyTechHealthScoreCalculator}. Reuses GetCompaniesUseCase's
 * Redis-cached company list (same id-lookup pattern as GetSimilarCompaniesUseCase) and the kafka
 * feature's {@link TechAliasResolver} to resolve raw tech-stack names to the canonical names
 * tech_analytics is keyed on.
 */
@Component
@RequiredArgsConstructor
public class GetCompanyTechHealthScoreUseCase {

    private final GetCompaniesUseCase getCompaniesUseCase;
    private final RadarQueryRepository radarQueryRepository;
    private final TechAliasResolver techAliasCache;

    public Mono<CompanyTechHealthScore> execute(String companyId) {
        return getCompaniesUseCase.all()
                .collectList()
                .flatMap(all -> {
                    var target = all.stream().filter(c -> c.id().equals(companyId)).findFirst();
                    if (target.isEmpty()) {
                        return Mono.error(new NotFoundException("Company not found: " + companyId));
                    }

                    List<String> stack = target.get().techStack();
                    if (stack.isEmpty()) {
                        return Mono.just(CompanyTechHealthScoreCalculator.compute(0, List.of()));
                    }

                    List<String> canonicalLower = stack.stream()
                            .map(techAliasCache::resolve)
                            .map(name -> name.toLowerCase(Locale.ROOT))
                            .distinct()
                            .toList();

                    return radarQueryRepository.findLatestSnapshotsForNames(canonicalLower)
                            .collectList()
                            .map(tracked -> CompanyTechHealthScoreCalculator.compute(stack.size(), tracked));
                });
    }
}
