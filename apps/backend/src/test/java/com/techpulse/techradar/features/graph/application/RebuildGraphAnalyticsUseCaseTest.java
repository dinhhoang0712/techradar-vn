package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import com.techpulse.techradar.features.graph.ports.GraphAnalyticsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RebuildGraphAnalyticsUseCaseTest {

    @Mock
    private GraphAnalyticsPort graphAnalyticsPort;

    private RebuildGraphAnalyticsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RebuildGraphAnalyticsUseCase(graphAnalyticsPort);
    }

    @Test
    void execute_delegatesToPort_andReturnsSummary() {
        GraphAnalyticsSummary summary = new GraphAnalyticsSummary(42, 7);
        when(graphAnalyticsPort.rebuild()).thenReturn(Mono.just(summary));

        StepVerifier.create(useCase.execute())
                .expectNext(summary)
                .verifyComplete();

        verify(graphAnalyticsPort).rebuild();
    }

    @Test
    void execute_propagatesPortError() {
        when(graphAnalyticsPort.rebuild()).thenReturn(Mono.error(new RuntimeException("GDS plugin missing")));

        StepVerifier.create(useCase.execute())
                .expectError(RuntimeException.class)
                .verify();
    }
}
