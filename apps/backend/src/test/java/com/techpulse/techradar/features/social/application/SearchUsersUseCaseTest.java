package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchUsersUseCaseTest {

    @Mock
    private FollowRepository followRepository;

    private SearchUsersUseCase useCase;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new SearchUsersUseCase(followRepository);
    }

    @Test
    void execute_returnsEmptyWithoutQueryingForANullOrBlankQuery() {
        StepVerifier.create(useCase.execute(viewerId.toString(), null, 8)).verifyComplete();
        StepVerifier.create(useCase.execute(viewerId.toString(), "   ", 8)).verifyComplete();
        verifyNoInteractions(followRepository);
    }

    @Test
    void execute_trimsTheQueryAndMapsRowsToUserSummary() {
        UUID userId = UUID.randomUUID();
        when(followRepository.searchByName(eq(viewerId), eq("an"), anyInt()))
                .thenReturn(Flux.just(new FollowRepository.UserSummaryRow(userId, "Nguyễn Văn An", "url")));

        StepVerifier.create(useCase.execute(viewerId.toString(), "  an  ", 8))
                .expectNext(new UserSummary(userId.toString(), "Nguyễn Văn An", "url"))
                .verifyComplete();
    }

    @Test
    void execute_defaultsLimitWhenNonPositive() {
        when(followRepository.searchByName(any(), any(), eq(8))).thenReturn(Flux.empty());
        useCase.execute(viewerId.toString(), "an", 0).blockLast();
        verify(followRepository).searchByName(viewerId, "an", 8);
    }

    @Test
    void execute_clampsLimitToTheMax() {
        when(followRepository.searchByName(any(), any(), eq(25))).thenReturn(Flux.empty());
        useCase.execute(viewerId.toString(), "an", 999).blockLast();
        verify(followRepository).searchByName(viewerId, "an", 25);
        verify(followRepository, never()).searchByName(viewerId, "an", 999);
    }
}
