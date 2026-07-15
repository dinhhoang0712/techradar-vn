package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetConversationsUseCaseTest {

    @Mock
    private ConversationRepository conversationRepository;

    private GetConversationsUseCase useCase;

    private final UUID userId = UUID.randomUUID();

    private void stubEmpty() {
        useCase = new GetConversationsUseCase(conversationRepository);
        when(conversationRepository.findAllForUser(any(UUID.class), anyInt(), anyInt())).thenReturn(Flux.empty());
    }

    @Test
    void execute_convertsPageAndSizeIntoLimitAndOffset() {
        stubEmpty();

        useCase.execute(userId.toString(), 2, 10).blockLast();

        verify(conversationRepository).findAllForUser(userId, 10, 20);
    }

    @Test
    void execute_clampsNegativePageToZero() {
        stubEmpty();

        useCase.execute(userId.toString(), -5, 10).blockLast();

        verify(conversationRepository).findAllForUser(userId, 10, 0);
    }

    @Test
    void execute_defaultsSizeTo20WhenNonPositive() {
        stubEmpty();

        useCase.execute(userId.toString(), 0, 0).blockLast();

        verify(conversationRepository).findAllForUser(userId, 20, 0);
    }

    @Test
    void execute_clampsSizeToMax100() {
        stubEmpty();

        useCase.execute(userId.toString(), 3, 500).blockLast();

        verify(conversationRepository).findAllForUser(userId, 100, 300);
    }

    @Test
    void execute_mapsRowToSummary_handlingNullLastSender() {
        useCase = new GetConversationsUseCase(conversationRepository);
        UUID conversationId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        ConversationRepository.ConversationRow row = new ConversationRepository.ConversationRow(
                conversationId, otherId, "Nguyễn Văn A", null, "Chào bạn!",
                LocalDateTime.of(2026, 7, 15, 10, 0), null, 2L);
        when(conversationRepository.findAllForUser(userId, 20, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(userId.toString(), 0, 20))
                .assertNext(summary -> {
                    assertThat(summary.id()).isEqualTo(conversationId.toString());
                    assertThat(summary.otherUser().id()).isEqualTo(otherId.toString());
                    assertThat(summary.otherUser().fullName()).isEqualTo("Nguyễn Văn A");
                    assertThat(summary.lastMessageContent()).isEqualTo("Chào bạn!");
                    assertThat(summary.lastMessageSenderId()).isNull();
                    assertThat(summary.unreadCount()).isEqualTo(2L);
                })
                .verifyComplete();
    }
}
