package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import com.techpulse.techradar.features.kgreview.ports.GraphMergePort;
import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveTechAliasUseCaseTest {

    @Mock
    private TechAliasReviewRepository repository;
    @Mock
    private GraphMergePort graphMergePort;

    private ApproveTechAliasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApproveTechAliasUseCase(repository, graphMergePort);
    }

    private TechAliasReviewItem item() {
        return new TechAliasReviewItem(1L, "Golang", "Go", "same language", "pending", LocalDateTime.now());
    }

    @Test
    void execute_withoutOverride_mergesNameAIntoNameB() {
        when(repository.findById(1L)).thenReturn(Mono.just(item()));
        when(graphMergePort.mergeTechnology("Golang", "Go")).thenReturn(Mono.just(true));
        when(repository.saveAlias("golang", "Go")).thenReturn(Mono.empty());
        when(repository.markApproved(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(1L, null)).verifyComplete();

        verify(graphMergePort).mergeTechnology("Golang", "Go");
        verify(repository).saveAlias("golang", "Go");
        verify(repository).markApproved(1L);
    }

    @Test
    void execute_withOverrideEqualToNameA_mergesNameBIntoNameA() {
        when(repository.findById(1L)).thenReturn(Mono.just(item()));
        when(graphMergePort.mergeTechnology(eq("Go"), eq("Golang"))).thenReturn(Mono.just(true));
        when(repository.saveAlias("go", "Golang")).thenReturn(Mono.empty());
        when(repository.markApproved(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(1L, "Golang")).verifyComplete();

        // The override becomes canonical, so the OTHER name ("Go") is the duplicate merged into it.
        verify(graphMergePort).mergeTechnology("Go", "Golang");
        verify(repository).saveAlias("go", "Golang");
    }

    @Test
    void execute_withOverrideNotMatchingEitherName_errorsWithoutTouchingGraph() {
        when(repository.findById(1L)).thenReturn(Mono.just(item()));

        StepVerifier.create(useCase.execute(1L, "Kubernetes"))
                .verifyError(IllegalArgumentException.class);

        verify(graphMergePort, never()).mergeTechnology(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void execute_itemNotFound_errorsWithNotFoundException() {
        when(repository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(99L, null))
                .verifyError(NotFoundException.class);
    }
}
