package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.kafka.KafkaNeo4jWriterService;
import com.techpulse.techradar.features.kafka.KafkaSyncStatus;
import com.techpulse.techradar.features.messaging.ports.MessagingStatsRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostAnalyticsRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.features.user.application.AdminUserService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregates admin dashboard metrics from across the codebase (social, jobs, messaging, the Kafka
 * pipeline, activity log) into one place, so {@code AdminDashboardController} stays a thin
 * request/response mapper instead of wiring 9 repositories and building each aggregation inline.
 */
@Component
@RequiredArgsConstructor
public class DashboardMetricsService {

    private static final int TOP_N = 10;

    private final AdminUserService userService;
    private final ActivityLogRepository activityLog;
    private final PostAnalyticsRepository postAnalyticsRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;
    private final JobRepository jobRepository;
    private final MessagingStatsRepository messagingStatsRepository;
    private final NotificationRepository notificationRepository;
    private final KafkaNeo4jWriterService kafkaNeo4jWriterService;

    public Mono<Long> userCount() {
        return userService.listUsers().count();
    }

    public Mono<Long> visitsToday() {
        return activityLog.countToday("visit");
    }

    public Mono<Long> searchesToday() {
        return activityLog.countToday("search");
    }

    public Mono<List<Map<String, Object>>> monthlyVisits() {
        return activityLog.monthlyVisits().collectList();
    }

    public Mono<List<String>> topKeywords() {
        return activityLog.topKeywords(TOP_N).collectList();
    }

    public Mono<SocialEngagementStats> socialEngagement() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Mono.zip(
                postAnalyticsRepository.countAll(),
                postAnalyticsRepository.countCreatedSince(todayStart),
                commentRepository.countAll(),
                postAnalyticsRepository.countAllLikes(),
                followRepository.countAll(),
                postAnalyticsRepository.topPosters(TOP_N).map(TopPoster::from).collectList(),
                reportRepository.countPending()
        ).map(t -> SocialEngagementStats.builder()
                .totalPosts(t.getT1())
                .postsToday(t.getT2())
                .totalComments(t.getT3())
                .totalLikes(t.getT4())
                .totalFollows(t.getT5())
                .topPosters(t.getT6())
                .pendingReports(t.getT7())
                .build());
    }

    public Mono<JobMarketStats> jobMarket() {
        return Mono.zip(
                jobRepository.countJobs(),
                jobRepository.topTechnologies(TOP_N).map(TechDemand::from).collectList(),
                notificationRepository.countGroupedByType().collectList()
        ).map(t -> JobMarketStats.builder()
                .totalJobsIndexed(t.getT1())
                .topTechnologies(t.getT2())
                .jobMatchAlertsSent(t.getT3().stream()
                        .filter(tc -> "JOB_MATCH".equals(tc.type()))
                        .mapToLong(NotificationRepository.TypeCount::count)
                        .findFirst().orElse(0L))
                .build());
    }

    public KafkaSyncStatus pipelineHealth() {
        return kafkaNeo4jWriterService.syncStatus();
    }

    public Mono<MessagingStats> messagingVolume() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Mono.zip(
                messagingStatsRepository.countConversations(),
                messagingStatsRepository.countMessages(),
                messagingStatsRepository.countMessagesSince(todayStart),
                notificationRepository.countGroupedByType()
                        .map(tc -> new NotificationTypeCount(tc.type(), tc.count()))
                        .collectList()
        ).map(t -> MessagingStats.builder()
                .totalConversations(t.getT1())
                .totalMessages(t.getT2())
                .messagesToday(t.getT3())
                .notificationsByType(t.getT4())
                .build());
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

        static TopPoster from(PostAnalyticsRepository.TopPosterRow r) {
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
