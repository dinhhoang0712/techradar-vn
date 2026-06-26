package com.techpulse.techradar.features.radar.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * Monthly job-count time series for the given technologies (backs /radar/search and /compare/search).
 * Results are cached in Redis (TTL configurable via app.redis.radar-cache-ttl).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTrendUseCase {

    private static final TypeReference<List<MonthlyCount>> LIST_TYPE = new TypeReference<>() {};
    static final String CACHE_KEY_PREFIX = "cache:radar:search:";

    private final RadarQueryRepository radarQueryRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.radar-cache-ttl:3600}")
    private long cacheTtlSeconds;

    public Flux<MonthlyCount> execute(List<String> keywords, int months) {
        if (keywords == null || keywords.isEmpty()) {
            log.warn("Search trend requested with no keywords; returning empty series");
            return Flux.empty();
        }
        int window = months <= 0 ? 6 : Math.min(months, 60);
        List<String> cleaned = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .sorted()
                .toList();
        if (cleaned.isEmpty()) {
            log.warn("Search trend requested with only blank keywords; returning empty series");
            return Flux.empty();
        }
        String cacheKey = CACHE_KEY_PREFIX + String.join(",", cleaned) + ":" + window;
        Duration ttl = Duration.ofSeconds(cacheTtlSeconds);
        log.info("Searching monthly trend for {} keyword(s) over {} months", cleaned.size(), window);
        return redisCache.getOrLoad(
                cacheKey,
                ttl,
                radarQueryRepository.monthlySeries(cleaned, window),
                LIST_TYPE
        );
    }
}
