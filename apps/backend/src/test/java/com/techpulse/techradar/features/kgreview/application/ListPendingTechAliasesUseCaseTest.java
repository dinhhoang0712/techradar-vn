package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPendingTechAliasesUseCaseTest {

    @Mock
    private TechAliasReviewRepository repository;

    private ListPendingTechAliasesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListPendingTechAliasesUseCase(repository);
    }

    @Test
    void execute_delegatesLimitAndOffsetToRepository() {
        TechAliasReviewItem item = new TechAliasReviewItem(1L, "Golang", "Go", null, "pending", LocalDateTime.now());
        when(repository.findPending(20, 40)).thenReturn(Flux.just(item));

        StepVerifier.create(useCase.execute(20, 40)).expectNext(item).verifyComplete();
    }

    @Test
    void count_delegatesToRepository() {
        when(repository.countPending()).thenReturn(Mono.just(3L));

        StepVerifier.create(useCase.count()).expectNext(3L).verifyComplete();
    }
}
