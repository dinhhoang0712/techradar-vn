package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostAnalyticsRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialEngagementMetricsServiceTest {

    @Mock
    private PostAnalyticsRepository postAnalyticsRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ReportRepository reportRepository;

    private SocialEngagementMetricsService service;

    @BeforeEach
    void setUp() {
        service = new SocialEngagementMetricsService(postAnalyticsRepository, commentRepository,
                followRepository, reportRepository);
    }

    @Test
    void socialEngagement_combinesCountsAndTopPosters() {
        UUID userId = UUID.randomUUID();
        when(postAnalyticsRepository.countAll()).thenReturn(Mono.just(50L));
        when(postAnalyticsRepository.countCreatedSince(any(LocalDateTime.class))).thenReturn(Mono.just(4L));
        when(commentRepository.countAll()).thenReturn(Mono.just(120L));
        when(postAnalyticsRepository.countAllLikes()).thenReturn(Mono.just(300L));
        when(followRepository.countAll()).thenReturn(Mono.just(80L));
        when(postAnalyticsRepository.topPosters(10)).thenReturn(Flux.just(
                new PostAnalyticsRepository.TopPosterRow(userId, "Dev One", 20L)));
        when(reportRepository.countPending()).thenReturn(Mono.just(2L));

        StepVerifier.create(service.socialEngagement())
                .assertNext(stats -> {
                    assertThat(stats.getTotalPosts()).isEqualTo(50L);
                    assertThat(stats.getPostsToday()).isEqualTo(4L);
                    assertThat(stats.getTotalComments()).isEqualTo(120L);
                    assertThat(stats.getTotalLikes()).isEqualTo(300L);
                    assertThat(stats.getTotalFollows()).isEqualTo(80L);
                    assertThat(stats.getPendingReports()).isEqualTo(2L);
                    assertThat(stats.getTopPosters()).hasSize(1);
                    assertThat(stats.getTopPosters().get(0).getUserId()).isEqualTo(userId.toString());
                    assertThat(stats.getTopPosters().get(0).getFullName()).isEqualTo("Dev One");
                    assertThat(stats.getTopPosters().get(0).getPostCount()).isEqualTo(20L);
                })
                .verifyComplete();
    }
}
