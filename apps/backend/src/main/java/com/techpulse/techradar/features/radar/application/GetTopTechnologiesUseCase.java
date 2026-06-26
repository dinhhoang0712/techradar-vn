package com.techpulse.techradar.features.radar.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
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
 * Top-N technologies by recent job count (backs /radar/top4, /radar/top10 and exports).
 * Results are cached in Redis (TTL configurable via app.redis.radar-cache-ttl).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTopTechnologiesUseCase {

    private static final int MAX_LIMIT = 100;
    private static final TypeReference<List<TechSnapshot>> LIST_TYPE = new TypeReference<>() {};
    static final String CACHE_KEY_PREFIX = "cache:radar:top:";

    private final RadarQueryRepository radarQueryRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.radar-cache-ttl:3600}")
    private long cacheTtlSeconds;

    public Flux<TechSnapshot> execute(int limit) {
        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, MAX_LIMIT);
        String cacheKey = CACHE_KEY_PREFIX + effectiveLimit;
        Duration ttl = Duration.ofSeconds(cacheTtlSeconds);
        log.info("Fetching top {} technologies (requested limit={})", effectiveLimit, limit);
        return redisCache.getOrLoad(
                cacheKey,
                ttl,
                radarQueryRepository.topTechnologies(effectiveLimit),
                LIST_TYPE
        );
    }
}
