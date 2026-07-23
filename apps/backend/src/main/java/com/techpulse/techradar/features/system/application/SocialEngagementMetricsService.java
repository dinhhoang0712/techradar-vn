package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostAnalyticsRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Social engagement metrics for the admin dashboard: posts, comments, likes, follows, most
 * active users, and the pending-moderation-report count.
 */
@Component
@RequiredArgsConstructor
public class SocialEngagementMetricsService {

    private static final int TOP_N = 10;

    private final PostAnalyticsRepository postAnalyticsRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;

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
}
