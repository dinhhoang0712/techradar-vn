package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import com.techpulse.techradar.features.system.application.JobMarketMetricsService;
import com.techpulse.techradar.features.system.application.LiveMetricsService;
import com.techpulse.techradar.features.system.application.MessagingMetricsService;
import com.techpulse.techradar.features.system.application.PipelineHealthService;
import com.techpulse.techradar.features.system.application.SiteMetricsService;
import com.techpulse.techradar.features.system.application.SocialEngagementMetricsService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Admin dashboard metrics, backed by the real user table and the {@code activity_log} traffic/search
 * events recorded by {@code ActivityTrackingFilter}. All aggregation lives in the per-area
 * {@code *MetricsService}/{@code PipelineHealthService} classes — this controller only maps
 * requests to them and wraps results.
 */
@Tag(name = "Admin", description = "Admin dashboard metrics")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final Duration LIVE_METRICS_INTERVAL = Duration.ofSeconds(5);

    private final SiteMetricsService siteMetrics;
    private final SocialEngagementMetricsService socialEngagementMetrics;
    private final JobMarketMetricsService jobMarketMetrics;
    private final PipelineHealthService pipelineHealthService;
    private final MessagingMetricsService messagingMetrics;
    private final LiveMetricsService liveMetricsService;

    @Operation(summary = "Total registered users")
    @GetMapping("/user-count")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<Long>>> userCount() {
        return siteMetrics.userCount().map(count -> ResponseEntity.ok(ApiResponse.success(count, "User count")));
    }

    @Operation(summary = "Visits today")
    @GetMapping("/visits-today")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<Long>>> visitsToday() {
        return siteMetrics.visitsToday().map(c -> ResponseEntity.ok(ApiResponse.success(c, "Visits today")));
    }

    @Operation(summary = "Searches performed today")
    @GetMapping("/searches-today")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<Long>>> searchesToday() {
        return siteMetrics.searchesToday().map(c -> ResponseEntity.ok(ApiResponse.success(c, "Searches today")));
    }

    @Operation(summary = "Monthly visit history (last 12 months)")
    @GetMapping("/monthly-visits")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> monthlyVisits() {
        return siteMetrics.monthlyVisits().map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Monthly visits")));
    }

    @Operation(summary = "Top search keywords")
    @GetMapping("/top-keywords")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> topKeywords() {
        return siteMetrics.topKeywords().map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Top keywords")));
    }

    @Operation(summary = "Social engagement: posts, comments, likes, follows, most active users")
    @GetMapping("/social")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<SocialEngagementMetricsService.SocialEngagementStats>>> socialEngagement() {
        return socialEngagementMetrics.socialEngagement()
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Social engagement")));
    }

    @Operation(summary = "Job & tech market: indexed jobs, top technologies, job-match alerts sent")
    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<JobMarketMetricsService.JobMarketStats>>> jobMarket() {
        return jobMarketMetrics.jobMarket().map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Job market")));
    }

    @Operation(summary = "Kafka to Neo4j data pipeline health")
    @GetMapping("/pipeline")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<KafkaSyncStatus>>> pipelineHealth() {
        return Mono.just(ResponseEntity.ok(ApiResponse.success(pipelineHealthService.pipelineHealth(), "Kafka sync status")));
    }

    @Operation(summary = "Messaging & notification volume")
    @GetMapping("/messaging")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Mono<ResponseEntity<ApiResponse<MessagingMetricsService.MessagingStats>>> messagingVolume() {
        return messagingMetrics.messagingVolume().map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Messaging volume")));
    }

    @Operation(summary = "Live metrics stream (SSE): articles crawled, new technologies, radar "
            + "rebuild status, AI requests today — re-polled every 5s")
    @GetMapping(value = "/live-metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Flux<ServerSentEvent<LiveMetricsService.LiveMetrics>> liveMetricsStream() {
        return Flux.interval(Duration.ZERO, LIVE_METRICS_INTERVAL)
                .flatMap(tick -> liveMetricsService.liveMetrics())
                .map(snapshot -> ServerSentEvent.builder(snapshot).event("live-metrics").build());
    }
}
