package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.features.user.application.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteMetricsServiceTest {

    @Mock
    private AdminUserService userService;
    @Mock
    private ActivityLogRepository activityLog;

    private SiteMetricsService service;

    @BeforeEach
    void setUp() {
        service = new SiteMetricsService(userService, activityLog);
    }

    @Test
    void userCount_countsAllUsersFromAdminUserService() {
        when(userService.listUsers()).thenReturn(Flux.just(
                com.techpulse.techradar.features.auth.domain.User.builder().build(),
                com.techpulse.techradar.features.auth.domain.User.builder().build()));

        StepVerifier.create(service.userCount()).expectNext(2L).verifyComplete();
    }

    @Test
    void visitsToday_delegatesToActivityLog() {
        when(activityLog.countToday("visit")).thenReturn(Mono.just(10L));

        StepVerifier.create(service.visitsToday()).expectNext(10L).verifyComplete();
    }

    @Test
    void searchesToday_delegatesToActivityLog() {
        when(activityLog.countToday("search")).thenReturn(Mono.just(5L));

        StepVerifier.create(service.searchesToday()).expectNext(5L).verifyComplete();
    }

    @Test
    void monthlyVisits_collectsRowsFromActivityLog() {
        when(activityLog.monthlyVisits()).thenReturn(Flux.just(Map.of("month", "2026-07", "visits", 100)));

        StepVerifier.create(service.monthlyVisits())
                .assertNext(rows -> org.assertj.core.api.Assertions.assertThat(rows).hasSize(1))
                .verifyComplete();
    }

    @Test
    void topKeywords_collectsTopTenFromActivityLog() {
        when(activityLog.topKeywords(10)).thenReturn(Flux.just("java", "kotlin"));

        StepVerifier.create(service.topKeywords())
                .expectNext(List.of("java", "kotlin"))
                .verifyComplete();
    }
}
