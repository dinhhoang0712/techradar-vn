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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictClusterUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private PredictClusterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PredictClusterUseCase(clusteringServicePort);
    }

    @Test
    void execute_returnsPredictedCluster_whenPortSucceeds() {
        when(clusteringServicePort.getTechCluster("React"))
                .thenReturn(Mono.just(Map.of("technology", "React", "cluster_id", 2)));

        StepVerifier.create(useCase.execute("React"))
                .expectNext(Map.of("technology", "React", "cluster_id", 2))
                .verifyComplete();
    }

    @Test
    void execute_rejectsNullTechnology() {
        StepVerifier.create(useCase.execute(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).getTechCluster(any());
    }

    @Test
    void execute_rejectsBlankTechnology() {
        StepVerifier.create(useCase.execute("   "))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).getTechCluster(any());
    }

    @Test
    void execute_propagatesPortError() {
        RuntimeException boom = new RuntimeException("ml-clustering unavailable");
        when(clusteringServicePort.getTechCluster("Unknown")).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute("Unknown"))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
