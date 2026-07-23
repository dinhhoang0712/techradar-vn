package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.radar.etl.RadarStatus;
import com.techpulse.techradar.features.system.application.JobMarketMetricsService;
import com.techpulse.techradar.features.system.application.LiveMetricsService;
import com.techpulse.techradar.features.system.application.MessagingMetricsService;
import com.techpulse.techradar.features.system.application.PipelineHealthService;
import com.techpulse.techradar.features.system.application.SiteMetricsService;
import com.techpulse.techradar.features.system.application.SocialEngagementMetricsService;
import com.techpulse.techradar.features.system.domain.CrawlerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private SiteMetricsService siteMetrics;
    @Mock
    private SocialEngagementMetricsService socialEngagementMetrics;
    @Mock
    private JobMarketMetricsService jobMarketMetrics;
    @Mock
    private PipelineHealthService pipelineHealthService;
    @Mock
    private MessagingMetricsService messagingMetrics;
    @Mock
    private LiveMetricsService liveMetricsService;

    private AdminDashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminDashboardController(siteMetrics, socialEngagementMetrics, jobMarketMetrics,
                pipelineHealthService, messagingMetrics, liveMetricsService);
    }

    @Test
    void userCount_returnsCountFromSiteMetrics() {
        when(siteMetrics.userCount()).thenReturn(Mono.just(42L));

        StepVerifier.create(controller.userCount())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(42L))
                .verifyComplete();
    }

    @Test
    void visitsToday_returnsCountFromSiteMetrics() {
        when(siteMetrics.visitsToday()).thenReturn(Mono.just(123L));

        StepVerifier.create(controller.visitsToday())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(123L))
                .verifyComplete();
    }

    @Test
    void searchesToday_returnsCountFromSiteMetrics() {
        when(siteMetrics.searchesToday()).thenReturn(Mono.just(7L));

        StepVerifier.create(controller.searchesToday())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void monthlyVisits_returnsRowsFromSiteMetrics() {
        var rows = java.util.List.of(java.util.Map.<String, Object>of("month", "2026-07", "visits", 100));
        when(siteMetrics.monthlyVisits()).thenReturn(Mono.just(rows));

        StepVerifier.create(controller.monthlyVisits())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(rows))
                .verifyComplete();
    }

    @Test
    void topKeywords_returnsKeywordsFromSiteMetrics() {
        when(siteMetrics.topKeywords()).thenReturn(Mono.just(java.util.List.of("java", "kotlin")));

        StepVerifier.create(controller.topKeywords())
                .assertNext(res -> assertThat(res.getBody().getData()).containsExactly("java", "kotlin"))
                .verifyComplete();
    }

    @Test
    void pipelineHealth_returnsStatusFromPipelineHealthService() {
        com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus status =
                new com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus(10L, 1L, 8L, 0L, null, null, null, null);
        when(pipelineHealthService.pipelineHealth()).thenReturn(status);

        StepVerifier.create(controller.pipelineHealth())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(status))
                .verifyComplete();
    }

    @Test
    void socialEngagement_returnsStatsFromSocialEngagementMetrics() {
        SocialEngagementMetricsService.SocialEngagementStats stats =
                SocialEngagementMetricsService.SocialEngagementStats.builder()
                        .totalPosts(10).postsToday(2).totalComments(5).totalLikes(20)
                        .totalFollows(3).topPosters(java.util.List.of()).pendingReports(1)
                        .build();
        when(socialEngagementMetrics.socialEngagement()).thenReturn(Mono.just(stats));

        StepVerifier.create(controller.socialEngagement())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(stats))
                .verifyComplete();
    }

    @Test
    void jobMarket_returnsStatsFromJobMarketMetrics() {
        JobMarketMetricsService.JobMarketStats stats = JobMarketMetricsService.JobMarketStats.builder()
                .totalJobsIndexed(100).topTechnologies(java.util.List.of()).jobMatchAlertsSent(7)
                .build();
        when(jobMarketMetrics.jobMarket()).thenReturn(Mono.just(stats));

        StepVerifier.create(controller.jobMarket())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(stats))
                .verifyComplete();
    }

    @Test
    void messagingVolume_returnsStatsFromMessagingMetrics() {
        MessagingMetricsService.MessagingStats stats = MessagingMetricsService.MessagingStats.builder()
                .totalConversations(5).totalMessages(50).messagesToday(4)
                .notificationsByType(java.util.List.of()).build();
        when(messagingMetrics.messagingVolume()).thenReturn(Mono.just(stats));

        StepVerifier.create(controller.messagingVolume())
                .assertNext(res -> assertThat(res.getBody().getData()).isEqualTo(stats))
                .verifyComplete();
    }

    @Test
    void liveMetricsStream_forwardsEachPollAsALiveMetricsServerSentEvent() {
        LiveMetricsService.LiveMetrics snapshot = LiveMetricsService.LiveMetrics.builder()
                .crawler(CrawlerStatus.neverRun())
                .radar(RadarStatus.neverRun())
                .newTechnologiesThisMonth(3L)
                .aiRequestsToday(42L)
                .build();
        when(liveMetricsService.liveMetrics()).thenReturn(Mono.just(snapshot));

        StepVerifier.create(controller.liveMetricsStream().take(1).timeout(Duration.ofSeconds(5)))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("live-metrics");
                    assertThat(sse.data()).isEqualTo(snapshot);
                })
                .verifyComplete();
    }
}
