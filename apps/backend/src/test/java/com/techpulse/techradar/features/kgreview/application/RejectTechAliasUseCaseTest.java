package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectTechAliasUseCaseTest {

    @Mock
    private TechAliasReviewRepository repository;

    private RejectTechAliasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RejectTechAliasUseCase(repository);
    }

    @Test
    void execute_pendingItem_completes() {
        when(repository.markRejected(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(1L)).verifyComplete();
    }

    @Test
    void execute_notPendingOrMissing_errorsWithNotFoundException() {
        when(repository.markRejected(1L)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(1L)).verifyError(NotFoundException.class);
    }
}
