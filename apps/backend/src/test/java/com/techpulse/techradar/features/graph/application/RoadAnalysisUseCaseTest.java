package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadAnalysisUseCaseTest {

    @Mock
    private GraphRepository graphRepository;

    private RoadAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RoadAnalysisUseCase(graphRepository);
    }

    @Test
    void execute_rejectsWhenFromIsNull() {
        StepVerifier.create(useCase.execute(null, "Senior Backend Dev"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex.getMessage()).isEqualTo("Both 'from' and 'to' are required");
                })
                .verify();

        verify(graphRepository, never()).shortestPathByName(anyString(), anyString());
    }

    @Test
    void execute_rejectsWhenFromIsBlank() {
        StepVerifier.create(useCase.execute("   ", "Senior Backend Dev"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(graphRepository, never()).shortestPathByName(anyString(), anyString());
    }

    @Test
    void execute_rejectsWhenToIsNull() {
        StepVerifier.create(useCase.execute("Junior Backend Dev", null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(graphRepository, never()).shortestPathByName(anyString(), anyString());
    }

    @Test
    void execute_rejectsWhenToIsBlank() {
        StepVerifier.create(useCase.execute("Junior Backend Dev", ""))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(graphRepository, never()).shortestPathByName(anyString(), anyString());
    }

    @Test
    void execute_returnsPathDataWhenFound() {
        GraphNode from = GraphNode.builder().id("1").name("Junior Backend Dev").type("Job").build();
        GraphNode to = GraphNode.builder().id("2").name("Senior Backend Dev").type("Job").build();
        GraphData expected = GraphData.builder()
                .nodes(List.of(from, to))
                .edges(List.of())
                .found(true)
                .build();
        when(graphRepository.shortestPathByName("Junior Backend Dev", "Senior Backend Dev"))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(useCase.execute("Junior Backend Dev", "Senior Backend Dev"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void execute_returnsNotFoundDataWithoutErrorWhenNoPathExists() {
        GraphData notFound = GraphData.builder()
                .nodes(List.of())
                .edges(List.of())
                .found(false)
                .build();
        when(graphRepository.shortestPathByName("A", "Z")).thenReturn(Mono.just(notFound));

        StepVerifier.create(useCase.execute("A", "Z"))
                .assertNext(data -> assertThat(data.isFound()).isFalse())
                .verifyComplete();
    }

    @Test
    void execute_propagatesRepositoryErrorUnchanged() {
        RuntimeException boom = new RuntimeException("neo4j down");
        when(graphRepository.shortestPathByName("A", "Z")).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute("A", "Z"))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
