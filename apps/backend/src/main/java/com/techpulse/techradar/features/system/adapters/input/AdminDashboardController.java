package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.kafka.KafkaNeo4jWriterService;
import com.techpulse.techradar.features.kafka.KafkaSyncStatus;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.features.user.application.AdminUserService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final int TOP_N = 10;

    private final AdminUserService userService;
    private final ActivityLogRepository activityLog;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;
    private final JobRepository jobRepository;
    private final ConversationRepository conversationRepository;
    private final NotificationRepository notificationRepository;
    private final KafkaNeo4jWriterService kafkaNeo4jWriterService;

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

    @Operation(summary = "Social engagement: posts, comments, likes, follows, most active users")
    @GetMapping("/social")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SocialEngagementStats>>> socialEngagement() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Mono.zip(
                postRepository.countAll(),
                postRepository.countCreatedSince(todayStart),
                commentRepository.countAll(),
                postRepository.countAllLikes(),
                followRepository.countAll(),
                postRepository.topPosters(TOP_N).map(TopPoster::from).collectList(),
                reportRepository.countPending()
        ).map(t -> ResponseEntity.ok(ApiResponse.success(
                SocialEngagementStats.builder()
                        .totalPosts(t.getT1())
                        .postsToday(t.getT2())
                        .totalComments(t.getT3())
                        .totalLikes(t.getT4())
                        .totalFollows(t.getT5())
                        .topPosters(t.getT6())
                        .pendingReports(t.getT7())
                        .build(),
                "Social engagement")));
    }

    @Operation(summary = "Job & tech market: indexed jobs, top technologies, job-match alerts sent")
    @GetMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<JobMarketStats>>> jobMarket() {
        return Mono.zip(
                jobRepository.countJobs(),
                jobRepository.topTechnologies(TOP_N).map(TechDemand::from).collectList(),
                notificationRepository.countGroupedByType().collectList()
        ).map(t -> ResponseEntity.ok(ApiResponse.success(
                JobMarketStats.builder()
                        .totalJobsIndexed(t.getT1())
                        .topTechnologies(t.getT2())
                        .jobMatchAlertsSent(t.getT3().stream()
                                .filter(tc -> "JOB_MATCH".equals(tc.type()))
                                .mapToLong(NotificationRepository.TypeCount::count)
                                .findFirst().orElse(0L))
                        .build(),
                "Job market")));
    }

    @Operation(summary = "Kafka to Neo4j data pipeline health")
    @GetMapping("/pipeline")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<KafkaSyncStatus>>> pipelineHealth() {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(kafkaNeo4jWriterService.syncStatus(), "Kafka sync status")));
    }

    @Operation(summary = "Messaging & notification volume")
    @GetMapping("/messaging")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<MessagingStats>>> messagingVolume() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Mono.zip(
                conversationRepository.countConversations(),
                conversationRepository.countMessages(),
                conversationRepository.countMessagesSince(todayStart),
                notificationRepository.countGroupedByType()
                        .map(tc -> new NotificationTypeCount(tc.type(), tc.count()))
                        .collectList()
        ).map(t -> ResponseEntity.ok(ApiResponse.success(
                MessagingStats.builder()
                        .totalConversations(t.getT1())
                        .totalMessages(t.getT2())
                        .messagesToday(t.getT3())
                        .notificationsByType(t.getT4())
                        .build(),
                "Messaging volume")));
    }

    @Value
    @Builder
    public static class SocialEngagementStats {
        long totalPosts;
        long postsToday;
        long totalComments;
        long totalLikes;
        long totalFollows;
        List<TopPoster> topPosters;
        long pendingReports;
    }

    @Value
    @Builder
    public static class TopPoster {
        String userId;
        String fullName;
        long postCount;

        static TopPoster from(PostRepository.TopPosterRow r) {
            return TopPoster.builder()
                    .userId(r.userId().toString())
                    .fullName(r.fullName())
                    .postCount(r.postCount())
                    .build();
        }
    }

    @Value
    @Builder
    public static class JobMarketStats {
        long totalJobsIndexed;
        List<TechDemand> topTechnologies;
        long jobMatchAlertsSent;
    }

    @Value
    @Builder
    public static class TechDemand {
        String name;
        long jobCount;

        static TechDemand from(JobRepository.TechDemandRaw r) {
            return TechDemand.builder().name(r.name()).jobCount(r.jobCount()).build();
        }
    }

    @Value
    @Builder
    public static class MessagingStats {
        long totalConversations;
        long totalMessages;
        long messagesToday;
        List<NotificationTypeCount> notificationsByType;
    }

    @Value
    public static class NotificationTypeCount {
        String type;
        long count;
    }
}
