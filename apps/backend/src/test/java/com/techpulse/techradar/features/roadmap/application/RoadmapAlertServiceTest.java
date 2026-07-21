package com.techpulse.techradar.features.roadmap.application;

import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.roadmap.domain.RoadmapResult;
import com.techpulse.techradar.features.roadmap.ports.AlertPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapAlertServiceTest {

    private static final double THRESHOLD = 30.0;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private GetCareerRoadmapUseCase getCareerRoadmapUseCase;

    @Mock
    private AlertPublisher alertPublisher;

    private RoadmapAlertService service;

    @BeforeEach
    void setUp() {
        service = new RoadmapAlertService(notificationRepository, getCareerRoadmapUseCase, alertPublisher);
        ReflectionTestUtils.setField(service, "trendThreshold", THRESHOLD);
    }

    private static TrendSubscriber candidate() {
        return new TrendSubscriber(UUID.randomUUID(), "user@techradar.vn", true, true);
    }

    private static RoadmapResult roadmapWithTopSkill(String techName, double growthRate) {
        return new RoadmapResult(true, List.of("Docker"),
                List.of(Map.<String, Object>of("tech_name", techName, "growth_rate", growthRate)),
                Map.of(), List.of());
    }

    @Test
    void runOnce_publishesAlertWhenTopSkillGrowthMeetsThreshold() {
        TrendSubscriber candidate = candidate();
        when(notificationRepository.findRoadmapCandidates()).thenReturn(Flux.just(candidate));
        when(getCareerRoadmapUseCase.execute(candidate.userId().toString()))
                .thenReturn(Mono.just(roadmapWithTopSkill("Kubernetes", 42.0)));

        StepVerifier.create(service.runOnce()).expectNext(1L).verifyComplete();

        ArgumentCaptor<RoadmapAlertEvent> captor = ArgumentCaptor.forClass(RoadmapAlertEvent.class);
        verify(alertPublisher).publish(captor.capture());
        RoadmapAlertEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(candidate.userId().toString());
        assertThat(event.getEmail()).isEqualTo(candidate.email());
        assertThat(event.isNotifyInapp()).isTrue();
        assertThat(event.isNotifyEmail()).isTrue();
        assertThat(event.getTechnology()).isEqualTo("Kubernetes");
        assertThat(event.getGrowthRate()).isEqualTo(42.0);
    }

    @Test
    void runOnce_skipsWhenTopSkillGrowthBelowThreshold() {
        TrendSubscriber candidate = candidate();
        when(notificationRepository.findRoadmapCandidates()).thenReturn(Flux.just(candidate));
        when(getCareerRoadmapUseCase.execute(candidate.userId().toString()))
                .thenReturn(Mono.just(roadmapWithTopSkill("Helm", 5.0)));

        StepVerifier.create(service.runOnce()).expectNext(0L).verifyComplete();

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    void runOnce_skipsWhenUserHasNoNextSkills() {
        TrendSubscriber candidate = candidate();
        when(notificationRepository.findRoadmapCandidates()).thenReturn(Flux.just(candidate));
        when(getCareerRoadmapUseCase.execute(candidate.userId().toString()))
                .thenReturn(Mono.just(new RoadmapResult(true, List.of("Docker"), List.of(), Map.of(), List.of())));

        StepVerifier.create(service.runOnce()).expectNext(0L).verifyComplete();

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    void runOnce_treatsPerUserFailureAsNoAlertRatherThanFailingTheWholeScan() {
        TrendSubscriber ok = candidate();
        TrendSubscriber failing = candidate();
        when(notificationRepository.findRoadmapCandidates()).thenReturn(Flux.just(ok, failing));
        when(getCareerRoadmapUseCase.execute(ok.userId().toString()))
                .thenReturn(Mono.just(roadmapWithTopSkill("Kubernetes", 50.0)));
        when(getCareerRoadmapUseCase.execute(failing.userId().toString()))
                .thenReturn(Mono.error(new RuntimeException("ai-rag-core unavailable")));

        StepVerifier.create(service.runOnce()).expectNext(1L).verifyComplete();
    }
}
