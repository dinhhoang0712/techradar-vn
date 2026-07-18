package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin override of an AI-generated cluster label (label/label_en/description/domain), for when
 * the LLM labeler mislabels or misjudges coherence. Evicts the public clusters-list cache so the
 * correction is visible immediately instead of waiting out the TTL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateClusterLabelUseCase {

    private static final String CLUSTERS_CACHE_PATTERN = "cache:clustering:clusters:*";

    private final ClusteringServicePort clusteringServicePort;
    private final ReactiveRedisCache redisCache;

    public Mono<Map<String, Object>> execute(
            String clusterId, String label, String labelEn, String description, String domain, String actor
    ) {
        if (clusterId == null || clusterId.isBlank()) {
            log.warn("Rejected cluster label override: blank cluster id");
            return Mono.error(new IllegalArgumentException("cluster id is required"));
        }

        Map<String, Object> body = new HashMap<>();
        if (label != null) body.put("label", label);
        if (labelEn != null) body.put("label_en", labelEn);
        if (description != null) body.put("description", description);
        if (domain != null) body.put("domain", domain);
        if (body.isEmpty()) {
            log.warn("Rejected cluster label override clusterId={}: no fields provided", clusterId);
            return Mono.error(new IllegalArgumentException(
                    "At least one of label/labelEn/description/domain is required"));
        }

        log.info("Admin overriding cluster label clusterId={} actor={} fields={}", clusterId, actor, body.keySet());
        return clusteringServicePort.updateClusterLabel(clusterId, body, actor)
                .flatMap(result -> redisCache.evictByPattern(CLUSTERS_CACHE_PATTERN).thenReturn(result));
    }
}
