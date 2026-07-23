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
class GetClusterUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private GetClusterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClusterUseCase(clusteringServicePort);
    }

    @Test
    void execute_returnsClusterDetail_whenPortSucceeds() {
        when(clusteringServicePort.getCluster("3")).thenReturn(Mono.just(Map.of("cluster_id", 3, "label", "Backend")));

        StepVerifier.create(useCase.execute("3"))
                .expectNext(Map.of("cluster_id", 3, "label", "Backend"))
                .verifyComplete();
    }

    @Test
    void execute_rejectsNullClusterId() {
        StepVerifier.create(useCase.execute(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).getCluster(any());
    }

    @Test
    void execute_rejectsBlankClusterId() {
        StepVerifier.create(useCase.execute("   "))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).getCluster(any());
    }

    @Test
    void execute_propagatesPortError() {
        RuntimeException boom = new RuntimeException("ml-clustering unavailable");
        when(clusteringServicePort.getCluster("404")).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute("404"))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
