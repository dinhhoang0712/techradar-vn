package com.techpulse.techradar.features.salary.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.salary.domain.SalaryParser;
import com.techpulse.techradar.features.salary.domain.SalaryStats;
import com.techpulse.techradar.features.salary.ports.SalaryRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Detailed salary stats + co-required technologies for one tech.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTechSalaryDetailUseCase {

    private static final TypeReference<SalaryInsight> SINGLE_TYPE = new TypeReference<>() {};
    private static final String CACHE_PREFIX = "cache:salary:tech:";

    private final SalaryRepository salaryRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.radar-cache-ttl:3600}")
    private long cacheTtlSeconds;

    public Mono<SalaryInsight> execute(String techName) {
        String cacheKey = CACHE_PREFIX + techName.toLowerCase();
        log.info("Fetching salary detail techName={}", techName);

        return redisCache.getOrLoadMono(
                cacheKey,
                Duration.ofSeconds(cacheTtlSeconds),
                buildDetail(techName),
                SINGLE_TYPE
        );
    }

    private Mono<SalaryInsight> buildDetail(String techName) {
        return salaryRepository.findTechSalaryDetail(techName)
                .flatMap(raw -> {
                    if (raw.totalJobs() == 0) {
                        return Mono.error(new NotFoundException("Technology not found: " + techName));
                    }

                    List<Double> midpoints = raw.salaries().stream()
                            .map(SalaryParser::parse)
                            .filter(opt -> opt.isPresent())
                            .map(opt -> opt.get().midpoint())
                            .toList();

                    List<String> coTechs = raw.coTechs().stream()
                            .map(e -> e.getKey())
                            .limit(8)
                            .toList();

                    if (midpoints.isEmpty()) {
                        return Mono.just(new SalaryInsight(
                                raw.techName(), raw.totalJobs(), 0,
                                0, 0, 0, 0, 0, 0, coTechs));
                    }

                    SalaryStats.Stats stats = SalaryStats.compute(midpoints);
                    return Mono.just(new SalaryInsight(
                            raw.techName(),
                            raw.totalJobs(),
                            midpoints.size(),
                            round(stats.median()),
                            round(stats.avg()),
                            round(stats.min()),
                            round(stats.max()),
                            round(stats.p25()),
                            round(stats.p75()),
                            coTechs
                    ));
                });
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}