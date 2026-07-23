package com.techpulse.techradar.features.roadmap.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapAlertSchedulerTest {

    @Mock
    private RoadmapAlertService roadmapAlertService;

    private RoadmapAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RoadmapAlertScheduler(roadmapAlertService);
    }

    @Test
    void scheduledScan_delegatesToRoadmapAlertService() {
        when(roadmapAlertService.runOnce()).thenReturn(Mono.just(3L));

        scheduler.scheduledScan();

        verify(roadmapAlertService).runOnce();
    }

    @Test
    void scheduledScan_doesNotPropagateFailure_whenServiceErrors() {
        when(roadmapAlertService.runOnce()).thenReturn(Mono.error(new RuntimeException("db unreachable")));

        assertThatCode(() -> scheduler.scheduledScan()).doesNotThrowAnyException();

        verify(roadmapAlertService).runOnce();
    }
}
