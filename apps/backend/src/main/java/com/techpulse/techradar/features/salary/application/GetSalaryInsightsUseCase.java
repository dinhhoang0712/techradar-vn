package com.techpulse.techradar.features.salary.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.salary.domain.SalaryParser;
import com.techpulse.techradar.features.salary.ports.SalaryRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Top N technologies ranked by median salary.
 * Results cached in Redis (TTL = app.redis.radar-cache-ttl, same as analytics).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetSalaryInsightsUseCase {

    private static final TypeReference<List<SalaryInsight>> LIST_TYPE = new TypeReference<>() {};
    private static final String CACHE_PREFIX = "cache:salary:top:";

    private final SalaryRepository salaryRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.radar-cache-ttl:3600}")
    private long cacheTtlSeconds;

    public Flux<SalaryInsight> execute(int limit, int minJobs) {
        int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        int effectiveMinJobs = minJobs <= 0 ? 1 : minJobs;
        String cacheKey = CACHE_PREFIX + effectiveLimit + ":" + effectiveMinJobs;

        log.info("Fetching salary insights limit={} minJobs={}", effectiveLimit, effectiveMinJobs);

        return redisCache.getOrLoad(
                cacheKey,
                Duration.ofSeconds(cacheTtlSeconds),
                buildInsights(effectiveMinJobs, effectiveLimit * 3),
                LIST_TYPE
        );
    }

    private Flux<SalaryInsight> buildInsights(int minJobs, int techLimit) {
        return salaryRepository.findTechSalaries(minJobs, techLimit)
                .flatMap(raw -> {
                    List<Double> midpoints = raw.salaries().stream()
                            .map(SalaryParser::parse)
                            .filter(opt -> opt.isPresent())
                            .map(opt -> opt.get().midpoint())
                            .toList();

                    if (midpoints.isEmpty()) {
                        return Mono.<SalaryInsight>empty();
                    }

                    return Mono.just(SalaryInsight.fromMidpoints(raw.techName(), raw.totalJobs(), midpoints, List.of()));
                })
                .sort((a, b) -> Double.compare(b.medianVnd(), a.medianVnd()));
    }
}