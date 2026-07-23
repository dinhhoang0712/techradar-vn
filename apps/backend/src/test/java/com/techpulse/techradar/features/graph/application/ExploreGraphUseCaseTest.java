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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExploreGraphUseCaseTest {

    @Mock
    private GraphRepository graphRepository;

    private ExploreGraphUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExploreGraphUseCase(graphRepository);
    }

    private static GraphData dataWith(boolean found, GraphNode... nodes) {
        return GraphData.builder()
                .nodes(List.of(nodes))
                .edges(List.of())
                .found(found)
                .build();
    }

    @Test
    void execute_rejectsWhenKeywordsIsNull() {
        StepVerifier.create(useCase.execute(null, 2, null, null))
                .expectErrorSatisfies(ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).isEqualTo("At least one keyword is required");
                })
                .verify();

        verify(graphRepository, never()).exploreByKeywords(anyList(), anyInt(), anyString(), anyLong());
    }

    @Test
    void execute_rejectsWhenKeywordsIsEmpty() {
        StepVerifier.create(useCase.execute(List.of(), 2, null, null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(graphRepository, never()).exploreByKeywords(anyList(), anyInt(), anyString(), anyLong());
    }

    @Test
    void execute_clampsDepthToDefaultWhenBelowMinimum() {
        when(graphRepository.exploreByKeywords(eq(List.of("java")), eq(2), isNull(), isNull()))
                .thenReturn(Mono.just(dataWith(true)));

        StepVerifier.create(useCase.execute(List.of("java"), 0, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(graphRepository).exploreByKeywords(List.of("java"), 2, null, null);
    }

    @Test
    void execute_clampsDepthToDefaultWhenAboveMaximum() {
        when(graphRepository.exploreByKeywords(eq(List.of("java")), eq(2), isNull(), isNull()))
                .thenReturn(Mono.just(dataWith(true)));

        StepVerifier.create(useCase.execute(List.of("java"), 4, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(graphRepository).exploreByKeywords(List.of("java"), 2, null, null);
    }

    @Test
    void execute_passesThroughValidDepthUnchanged() {
        when(graphRepository.exploreByKeywords(eq(List.of("java")), eq(3), isNull(), isNull()))
                .thenReturn(Mono.just(dataWith(true)));

        StepVerifier.create(useCase.execute(List.of("java"), 3, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(graphRepository).exploreByKeywords(List.of("java"), 3, null, null);
    }

    @Test
    void execute_passesLocationAndMinSalaryThroughToRepository() {
        when(graphRepository.exploreByKeywords(List.of("java", "react"), 2, "Hà Nội", 1000L))
                .thenReturn(Mono.just(dataWith(true)));

        StepVerifier.create(useCase.execute(List.of("java", "react"), 2, "Hà Nội", 1000L))
                .expectNextCount(1)
                .verifyComplete();

        verify(graphRepository).exploreByKeywords(List.of("java", "react"), 2, "Hà Nội", 1000L);
    }

    @Test
    void execute_returnsGraphDataFromRepositoryOnSuccess() {
        GraphNode node = GraphNode.builder().id("1").name("Java").type("Skill").build();
        GraphData expected = dataWith(true, node);
        when(graphRepository.exploreByKeywords(anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(useCase.execute(List.of("java"), 2, null, null))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void execute_propagatesRepositoryErrorUnchanged() {
        RuntimeException boom = new RuntimeException("neo4j down");
        when(graphRepository.exploreByKeywords(anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute(List.of("java"), 2, null, null))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
