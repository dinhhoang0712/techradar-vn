package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPipelineRunsUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private GetPipelineRunsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPipelineRunsUseCase(clusteringServicePort);
    }

    @Test
    void execute_delegatesToPortAndReturnsAllRunsUncached() {
        when(clusteringServicePort.getPipelineRuns()).thenReturn(Flux.just(
                Map.of("run_id", "run-1", "metrics", Map.of("silhouette", 0.4)),
                Map.of("run_id", "run-2", "metrics", Map.of("silhouette", 0.5))
        ));

        StepVerifier.create(useCase.execute())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void execute_propagatesPortError() {
        RuntimeException boom = new RuntimeException("mlflow unreachable");
        when(clusteringServicePort.getPipelineRuns()).thenReturn(Flux.error(boom));

        StepVerifier.create(useCase.execute())
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
