package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.etl.RadarAnalyticsEtlService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin-triggered rebuild of the {@code tech_analytics} table from the knowledge graph.
 */
@Tag(name = "Admin", description = "Analytics ETL")
@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final RadarAnalyticsEtlService etlService;
    private final ReactiveRedisCache redisCache;

    @Operation(summary = "Rebuild radar/compare analytics from Neo4j")
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> rebuild() {
        return etlService.rebuild()
                .flatMap(count -> redisCache.evictByPattern("cache:radar:*").thenReturn(count))
                .map(count -> ResponseEntity.ok(
                        ApiResponse.success(Map.<String, Object>of("rows_upserted", count), "Analytics rebuilt")))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(503).body(
                        ApiResponse.error("Analytics rebuild failed: " + ex.getMessage(), "ETL_ERROR"))));
    }
}
