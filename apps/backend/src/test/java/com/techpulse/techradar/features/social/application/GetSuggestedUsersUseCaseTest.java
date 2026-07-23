package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.UserDirectoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSuggestedUsersUseCaseTest {

    @Mock
    private UserDirectoryRepository userDirectoryRepository;

    private GetSuggestedUsersUseCase useCase;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetSuggestedUsersUseCase(userDirectoryRepository);
    }

    @Test
    void execute_mapsRowsToUserSummary() {
        UUID suggestedId = UUID.randomUUID();
        when(userDirectoryRepository.suggested(eq(viewerId), eq(10))).thenReturn(Flux.just(
                new UserDirectoryRepository.UserSummaryRow(suggestedId, "An Nguyen", "avatar.png")
        ));

        StepVerifier.create(useCase.execute(viewerId.toString(), 10))
                .expectNext(new UserSummary(suggestedId.toString(), "An Nguyen", "avatar.png"))
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyWhenThereAreNoSuggestions() {
        when(userDirectoryRepository.suggested(eq(viewerId), eq(10))).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(viewerId.toString(), 10)).verifyComplete();
    }

    @Test
    void execute_defaultsLimitWhenNonPositive() {
        when(userDirectoryRepository.suggested(any(), eq(10))).thenReturn(Flux.empty());

        useCase.execute(viewerId.toString(), 0).blockLast();

        verify(userDirectoryRepository).suggested(viewerId, 10);
    }

    @Test
    void execute_clampsLimitToTheMax() {
        when(userDirectoryRepository.suggested(any(), eq(50))).thenReturn(Flux.empty());

        useCase.execute(viewerId.toString(), 999).blockLast();

        verify(userDirectoryRepository).suggested(viewerId, 50);
        verify(userDirectoryRepository, never()).suggested(viewerId, 999);
    }
}
