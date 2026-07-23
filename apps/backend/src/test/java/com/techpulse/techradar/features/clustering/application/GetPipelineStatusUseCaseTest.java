package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPipelineStatusUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private GetPipelineStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPipelineStatusUseCase(clusteringServicePort);
    }

    @Test
    void execute_delegatesDirectlyToPortWithoutCaching() {
        when(clusteringServicePort.getPipelineStatus())
                .thenReturn(Mono.just(Map.of("status", "running", "current_stage", "pipelines.stage_02_features")));

        StepVerifier.create(useCase.execute())
                .expectNext(Map.of("status", "running", "current_stage", "pipelines.stage_02_features"))
                .verifyComplete();
    }

    @Test
    void execute_propagatesPortError() {
        RuntimeException boom = new RuntimeException("ml-clustering unreachable");
        when(clusteringServicePort.getPipelineStatus()).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute())
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
