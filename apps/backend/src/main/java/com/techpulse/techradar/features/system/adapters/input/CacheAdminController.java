package com.techpulse.techradar.features.system.adapters.input;

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

/**
 * Admin-triggered cache eviction for lookaside caches that have no ETL/rebuild step to hang an
 * eviction off of. Company/job tech-stack data streams in continuously (Kafka + batch
 * data-platform sync) — unlike radar's {@code tech_analytics} (see
 * {@link com.techpulse.techradar.features.radar.adapters.input.AnalyticsAdminController}), there's
 * no rebuild to trigger, just a cache that goes stale for up to {@code app.redis.company-cache-ttl}
 * / {@code app.redis.job-cache-ttl} seconds after new data lands (see docs/DATABASE.md §5).
 */
@Tag(name = "Admin", description = "Cache eviction")
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final ReactiveRedisCache redisCache;

    @Operation(summary = "Evict the cached company list (GetCompaniesUseCase / GetSimilarCompaniesUseCase)")
    @PostMapping("/companies/evict")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> evictCompanies() {
        return redisCache.evict("cache:company:all")
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Company cache evicted")));
    }

    @Operation(summary = "Evict all cached job-match results (GetJobMatchesUseCase — one entry per distinct skill set)")
    @PostMapping("/jobs/evict")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> evictJobs() {
        return redisCache.evictByPattern("cache:job:match:*")
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Job match cache evicted")));
    }

    @Operation(summary = "Evict all cached career roadmaps (GetCareerRoadmapUseCase — one entry per user)")
    @PostMapping("/roadmap/evict")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> evictRoadmaps() {
        return redisCache.evictByPattern("cache:roadmap:*")
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Roadmap cache evicted")));
    }
}
