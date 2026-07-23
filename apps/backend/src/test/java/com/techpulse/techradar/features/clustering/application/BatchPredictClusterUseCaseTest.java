package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchPredictClusterUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;

    private BatchPredictClusterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BatchPredictClusterUseCase(clusteringServicePort);
    }

    @Test
    void execute_predictsBatchForCleanedTechnologyNames() {
        when(clusteringServicePort.predictBatch(List.of("Java", "React", "Go")))
                .thenReturn(Mono.just(Map.of("results", List.of(Map.of("technology", "Java", "cluster_id", 1)))));

        StepVerifier.create(useCase.execute(Arrays.asList(" Java ", "React", "Go")))
                .assertNext(result -> assertThat(result).containsKey("results"))
                .verifyComplete();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(clusteringServicePort).predictBatch(captor.capture());
        assertThat(captor.getValue()).containsExactly("Java", "React", "Go");
    }

    @Test
    void execute_trimsWhitespaceAndDropsBlankOrNullEntriesBeforeCallingPort() {
        when(clusteringServicePort.predictBatch(List.of("Kotlin")))
                .thenReturn(Mono.just(Map.of("results", List.of())));

        StepVerifier.create(useCase.execute(Arrays.asList("  Kotlin  ", "", "   ", null)))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(clusteringServicePort).predictBatch(captor.capture());
        assertThat(captor.getValue()).containsExactly("Kotlin");
    }

    @Test
    void execute_rejectsNullTechnologyList() {
        StepVerifier.create(useCase.execute(null))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex).hasMessage("At least one technology is required");
                })
                .verify();

        verify(clusteringServicePort, never()).predictBatch(any());
    }

    @Test
    void execute_rejectsEmptyTechnologyList() {
        StepVerifier.create(useCase.execute(List.of()))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex).hasMessage("At least one technology is required");
                })
                .verify();

        verify(clusteringServicePort, never()).predictBatch(any());
    }

    @Test
    void execute_rejectsListContainingOnlyBlankOrNullEntries() {
        StepVerifier.create(useCase.execute(Arrays.asList(null, "", "   ")))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex).hasMessage("At least one technology is required");
                })
                .verify();

        verify(clusteringServicePort, never()).predictBatch(any());
    }

    @Test
    void execute_propagatesPythonServiceError() {
        RuntimeException boom = new RuntimeException("ml-clustering unavailable");
        when(clusteringServicePort.predictBatch(List.of("Java"))).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute(List.of("Java")))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
