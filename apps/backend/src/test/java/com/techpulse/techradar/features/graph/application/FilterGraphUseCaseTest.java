package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilterGraphUseCaseTest {

    @Mock
    private GraphRepository graphRepository;

    private FilterGraphUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FilterGraphUseCase(graphRepository);
    }

    @Test
    void execute_rejectsWhenFilterIsNull() {
        StepVerifier.create(useCase.execute(null))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex.getMessage()).isEqualTo("Filter is required");
                })
                .verify();

        verify(graphRepository, never()).filterNodes(any());
    }

    @Test
    void execute_returnsNodesFromRepositoryOnSuccess() {
        GraphFilter filter = GraphFilter.builder()
                .locations(List.of("Hà Nội"))
                .nodeTypes(List.of("Job"))
                .minSalary(1000)
                .maxSalary(2000)
                .sentiment("positive")
                .build();
        GraphNode node1 = GraphNode.builder().id("1").name("Backend Dev").type("Job").build();
        GraphNode node2 = GraphNode.builder().id("2").name("Frontend Dev").type("Job").build();
        when(graphRepository.filterNodes(filter)).thenReturn(Flux.just(node1, node2));

        StepVerifier.create(useCase.execute(filter))
                .expectNext(node1, node2)
                .verifyComplete();

        verify(graphRepository).filterNodes(filter);
    }

    @Test
    void execute_returnsEmptyFluxWhenNoNodesMatch() {
        GraphFilter filter = GraphFilter.builder().locations(List.of("Nowhere")).build();
        when(graphRepository.filterNodes(filter)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(filter))
                .verifyComplete();
    }

    @Test
    void execute_propagatesRepositoryErrorUnchanged() {
        GraphFilter filter = GraphFilter.builder().build();
        RuntimeException boom = new RuntimeException("neo4j down");
        when(graphRepository.filterNodes(filter)).thenReturn(Flux.error(boom));

        StepVerifier.create(useCase.execute(filter))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
