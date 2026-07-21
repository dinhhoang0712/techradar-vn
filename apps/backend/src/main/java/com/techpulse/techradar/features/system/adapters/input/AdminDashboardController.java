package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.kafka.KafkaSyncStatus;
import com.techpulse.techradar.features.system.application.DashboardMetricsService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin dashboard metrics, backed by the real user table and the {@code activity_log} traffic/search
 * events recorded by {@code ActivityTrackingFilter}. All aggregation lives in
 * {@link DashboardMetricsService} — this controller only maps requests to it and wraps results.
 */
@Tag(name = "Admin", description = "Admin dashboard metrics")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardMetricsService metrics;

    @Operation(summary = "Total registered users")
    @GetMapping("/user-count")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> userCount() {
        return metrics.userCount().map(count -> ResponseEntity.ok(ApiResponse.success(count, "User count")));
    }

    @Operation(summary = "Visits today")
    @GetMapping("/visits-today")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> visitsToday() {
        return metrics.visitsToday().map(c -> ResponseEntity.ok(ApiResponse.success(c, "Visits today")));
    }

    @Operation(summary = "Searches performed today")
    @GetMapping("/searches-today")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> searchesToday() {
        return metrics.searchesToday().map(c -> ResponseEntity.ok(ApiResponse.success(c, "Searches today")));
    }

    @Operation(summary = "Monthly visit history (last 12 months)")
    @GetMapping("/monthly-visits")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> monthlyVisits() {
        return metrics.monthlyVisits().map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Monthly visits")));
    }

    @Operation(summary = "Top search keywords")
    @GetMapping("/top-keywords")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> topKeywords() {
        return metrics.topKeywords().map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Top keywords")));
    }

    @Operation(summary = "Social engagement: posts, comments, likes, follows, most active users")
    @GetMapping("/social")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<DashboardMetricsService.SocialEngagementStats>>> socialEngagement() {
        return metrics.socialEngagement()
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Social engagement")));
    }

    @Operation(summary = "Job & tech market: indexed jobs, top technologies, job-match alerts sent")
    @GetMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<DashboardMetricsService.JobMarketStats>>> jobMarket() {
        return metrics.jobMarket().map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Job market")));
    }

    @Operation(summary = "Kafka to Neo4j data pipeline health")
    @GetMapping("/pipeline")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<KafkaSyncStatus>>> pipelineHealth() {
        return Mono.just(ResponseEntity.ok(ApiResponse.success(metrics.pipelineHealth(), "Kafka sync status")));
    }

    @Operation(summary = "Messaging & notification volume")
    @GetMapping("/messaging")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<DashboardMetricsService.MessagingStats>>> messagingVolume() {
        return metrics.messagingVolume().map(stats -> ResponseEntity.ok(ApiResponse.success(stats, "Messaging volume")));
    }
}
