package com.techpulse.techradar.features.system.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.auth.ports.UserStatsRepository;
import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.system.domain.PublicStats;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Real aggregate counts (companies/jobs/users) for the public login/register stat chips.
 * Companies reuses {@link GetCompaniesUseCase}'s own cached list (no extra query); jobs/users are
 * cheap dedicated COUNT queries, bundled together behind one Redis-cached entry so an unauthenticated
 * page hit by every visitor doesn't hammer Postgres/Neo4j.
 */
@Component
@RequiredArgsConstructor
public class GetPublicStatsUseCase {

    private static final TypeReference<PublicStats> TYPE = new TypeReference<>() {};
    private static final String CACHE_KEY = "cache:public-stats";

    private final GetCompaniesUseCase getCompaniesUseCase;
    private final JobRepository jobRepository;
    private final UserStatsRepository userStatsRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.public-stats-cache-ttl:1800}")
    private long cacheTtlSeconds;

    public Mono<PublicStats> execute() {
        return redisCache.getOrLoadMono(
                CACHE_KEY,
                Duration.ofSeconds(cacheTtlSeconds),
                Mono.zip(
                        getCompaniesUseCase.all().count(),
                        jobRepository.countJobs(),
                        userStatsRepository.countAll()
                ).map(t -> new PublicStats(t.getT1(), t.getT2(), t.getT3())),
                TYPE
        );
    }
}
