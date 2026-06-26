package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.features.user.application.UserService;
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
 * events recorded by {@code ActivityTrackingFilter}.
 */
@Tag(name = "Admin", description = "Admin dashboard metrics")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserService userService;
    private final ActivityLogRepository activityLog;

    @Operation(summary = "Total registered users")
    @GetMapping("/user-count")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> userCount() {
        return userService.listUsers().count()
                .map(count -> ResponseEntity.ok(ApiResponse.success(count, "User count")));
    }

    @Operation(summary = "Visits today")
    @GetMapping("/visits-today")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> visitsToday() {
        return activityLog.countToday("visit")
                .map(c -> ResponseEntity.ok(ApiResponse.success(c, "Visits today")));
    }

    @Operation(summary = "Searches performed today")
    @GetMapping("/searches-today")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> searchesToday() {
        return activityLog.countToday("search")
                .map(c -> ResponseEntity.ok(ApiResponse.success(c, "Searches today")));
    }

    @Operation(summary = "Monthly visit history (last 12 months)")
    @GetMapping("/monthly-visits")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> monthlyVisits() {
        return activityLog.monthlyVisits()
                .collectList()
                .map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Monthly visits")));
    }

    @Operation(summary = "Top search keywords")
    @GetMapping("/top-keywords")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> topKeywords() {
        return activityLog.topKeywords(10)
                .collectList()
                .map(rows -> ResponseEntity.ok(ApiResponse.success(rows, "Top keywords")));
    }
}
