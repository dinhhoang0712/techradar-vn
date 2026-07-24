package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.ports.GraphMergePort;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MergeCompanyDuplicateUseCaseTest {

    @Mock
    private GraphMergePort graphMergePort;

    private MergeCompanyDuplicateUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MergeCompanyDuplicateUseCase(graphMergePort);
    }

    @Test
    void execute_validPair_delegatesToGraphMergePort() {
        when(graphMergePort.mergeCompany("fpt-software", "fpt-corp")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("fpt-software", "fpt-corp")).verifyComplete();
    }

    @Test
    void execute_sameIdForBoth_errorsWithoutTouchingGraph() {
        StepVerifier.create(useCase.execute("fpt-software", "fpt-software"))
                .verifyError(IllegalArgumentException.class);

        verify(graphMergePort, never()).mergeCompany(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void execute_blankDuplicateId_errorsWithoutTouchingGraph() {
        StepVerifier.create(useCase.execute("  ", "fpt-corp"))
                .verifyError(IllegalArgumentException.class);

        verify(graphMergePort, never()).mergeCompany(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void execute_neitherCompanyExists_errorsWithNotFoundException() {
        when(graphMergePort.mergeCompany("a", "b")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute("a", "b")).verifyError(NotFoundException.class);
    }
}
