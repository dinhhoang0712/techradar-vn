package com.techpulse.techradar.features.roadmap.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.roadmap.domain.SimulationResult;
import com.techpulse.techradar.features.salary.application.GetTechSalaryDetailUseCase;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "What if I learned this technology?" — previews the effect of a hypothetical skill on the
 * user's job-match count, its market salary, and its trend forecast, without persisting anything
 * to the profile. Pure orchestration over three already-real engines: {@link GetJobMatchesUseCase}
 * (graph scoring), {@link GetTechSalaryDetailUseCase} (real salary stats from crawled postings),
 * and ai-rag-core's {@code /forecast} (statistical signals + LLM synthesis, via {@link AiProxyPort}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulateCareerMoveUseCase {

    private static final int MATCH_SAMPLE_LIMIT = 100;
    private static final TypeReference<SimulationResult> CACHE_TYPE = new TypeReference<>() {};

    private final UserProfileRepository userProfileRepository;
    private final GetJobMatchesUseCase getJobMatchesUseCase;
    private final GetTechSalaryDetailUseCase getTechSalaryDetailUseCase;
    private final AiProxyPort aiProxyPort;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.roadmap-cache-ttl:1800}")
    private long cacheTtlSeconds;

    public Mono<SimulationResult> execute(String userId, String technology) {
        String tech = technology == null ? "" : technology.trim();
        if (tech.isBlank()) {
            return Mono.error(new IllegalArgumentException("technology is required"));
        }

        return userProfileRepository.findByUserId(userId)
                .map(profile -> profile.getTechnologies() == null ? List.<String>of() : profile.getTechnologies())
                .defaultIfEmpty(List.of())
                .flatMap(currentSkills -> redisCache.getOrLoadMono(
                        "cache:simulate:" + userId + ":" + tech.toLowerCase(Locale.ROOT),
                        Duration.ofSeconds(cacheTtlSeconds),
                        buildSimulation(tech, currentSkills),
                        CACHE_TYPE));
    }

    private Mono<SimulationResult> buildSimulation(String tech, List<String> currentSkills) {
        List<String> withHypothetical = withTech(currentSkills, tech);

        Mono<Long> currentCountMono = getJobMatchesUseCase.executeForSkills(currentSkills, MATCH_SAMPLE_LIMIT).count();
        Mono<Long> simulatedCountMono = getJobMatchesUseCase.executeForSkills(withHypothetical, MATCH_SAMPLE_LIMIT).count();
        Mono<Map<String, Object>> forecastMono = aiProxyPort
                .forward("/forecast", Map.of("technology", tech, "horizon_months", 6), AiProxyPort.DEFAULT_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Simulate: /forecast call failed for {}", tech, e);
                    return Mono.just(Map.of());
                });
        Mono<SalaryInsight> salaryMono = getTechSalaryDetailUseCase.execute(tech)
                .onErrorResume(NotFoundException.class, e -> Mono.empty());

        return Mono.zip(currentCountMono, simulatedCountMono, forecastMono)
                .flatMap(tuple -> salaryMono
                        .map(salary -> new SimulationResult(tech, tuple.getT1(), tuple.getT2(), salary, tuple.getT3()))
                        .switchIfEmpty(Mono.fromSupplier(() ->
                                new SimulationResult(tech, tuple.getT1(), tuple.getT2(), null, tuple.getT3()))));
    }

    private static List<String> withTech(List<String> currentSkills, String tech) {
        boolean alreadyHave = currentSkills.stream().anyMatch(s -> s.equalsIgnoreCase(tech));
        if (alreadyHave) {
            return currentSkills;
        }
        List<String> combined = new ArrayList<>(currentSkills);
        combined.add(tech);
        return combined;
    }
}
