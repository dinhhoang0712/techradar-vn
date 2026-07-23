package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMarketMetricsServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private JobMarketMetricsService service;

    @BeforeEach
    void setUp() {
        service = new JobMarketMetricsService(jobRepository, notificationRepository);
    }

    @Test
    void jobMarket_combinesJobCountTopTechAndJobMatchAlertCount() {
        when(jobRepository.countJobs()).thenReturn(Mono.just(500L));
        when(jobRepository.topTechnologies(10)).thenReturn(Flux.just(
                new JobRepository.TechDemandRaw("Java", 200L),
                new JobRepository.TechDemandRaw("Python", 150L)));
        when(notificationRepository.countGroupedByType()).thenReturn(Flux.just(
                new NotificationRepository.TypeCount("JOB_MATCH", 30L),
                new NotificationRepository.TypeCount("TREND_ALERT", 10L)));

        StepVerifier.create(service.jobMarket())
                .assertNext(stats -> {
                    assertThat(stats.getTotalJobsIndexed()).isEqualTo(500L);
                    assertThat(stats.getTopTechnologies()).hasSize(2);
                    assertThat(stats.getTopTechnologies().get(0).getName()).isEqualTo("Java");
                    assertThat(stats.getJobMatchAlertsSent()).isEqualTo(30L);
                })
                .verifyComplete();
    }

    @Test
    void jobMarket_defaultsJobMatchAlertsSentToZero_whenNoJobMatchTypePresent() {
        when(jobRepository.countJobs()).thenReturn(Mono.just(10L));
        when(jobRepository.topTechnologies(10)).thenReturn(Flux.empty());
        when(notificationRepository.countGroupedByType()).thenReturn(Flux.just(
                new NotificationRepository.TypeCount("TREND_ALERT", 5L)));

        StepVerifier.create(service.jobMarket())
                .assertNext(stats -> assertThat(stats.getJobMatchAlertsSent()).isZero())
                .verifyComplete();
    }
}
