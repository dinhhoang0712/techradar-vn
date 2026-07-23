package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
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
class TriggerPipelineUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private TriggerPipelineUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TriggerPipelineUseCase(clusteringServicePort);
    }

    @Test
    void execute_delegatesToPortAndReturnsStartedRunInfo() {
        when(clusteringServicePort.triggerPipeline())
                .thenReturn(Mono.just(Map.of("status", "started", "run_id", "run-42")));

        StepVerifier.create(useCase.execute())
                .expectNext(Map.of("status", "started", "run_id", "run-42"))
                .verifyComplete();
    }

    @Test
    void execute_propagatesConflict_whenPipelineAlreadyRunning() {
        ConflictException conflict = new ConflictException(ErrorCode.PIPELINE_RUNNING, "Đang chạy");
        when(clusteringServicePort.triggerPipeline()).thenReturn(Mono.error(conflict));

        StepVerifier.create(useCase.execute())
                .expectErrorMatches(ex -> ex instanceof ConflictException
                        && ((ConflictException) ex).getStatusCode() == 409)
                .verify();
    }

    @Test
    void execute_propagatesGenericPortError() {
        RuntimeException boom = new RuntimeException("ml-clustering unreachable");
        when(clusteringServicePort.triggerPipeline()).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute())
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
