package com.techpulse.techradar.features.clustering.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * List technology clusters, optionally filtered by coherence.
 * Results are cached in Redis (TTL configurable via app.redis.clustering-cache-ttl).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetClustersUseCase {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};
    private static final String CACHE_KEY_PREFIX = "cache:clustering:clusters:";

    private final ClusteringServicePort clusteringServicePort;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.clustering-cache-ttl:900}")
    private long cacheTtlSeconds;

    public Flux<Map<String, Object>> execute(Boolean isCoherent) {
        String cacheKey = CACHE_KEY_PREFIX + isCoherent;
        Duration ttl = Duration.ofSeconds(cacheTtlSeconds);
        log.info("Listing technology clusters isCoherent={}", isCoherent);
        return redisCache.getOrLoad(
                cacheKey,
                ttl,
                clusteringServicePort.getClusters(isCoherent),
                LIST_MAP_TYPE
        );
    }
}
