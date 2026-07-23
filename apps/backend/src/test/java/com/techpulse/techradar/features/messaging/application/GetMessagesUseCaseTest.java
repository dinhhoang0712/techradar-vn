package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMessagesUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;

    @Mock
    private MessageRepository messageRepository;

    private GetMessagesUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    @Test
    void execute_returnsMessagesOldestFirstMappedToDomain() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 9, 30);
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, "Chào bạn", createdAt, null);
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .assertNext(message -> {
                    assertThat(message.id()).isEqualTo(messageId.toString());
                    assertThat(message.conversationId()).isEqualTo(conversationId.toString());
                    assertThat(message.senderId()).isEqualTo(senderId.toString());
                    assertThat(message.content()).isEqualTo("Chào bạn");
                    assertThat(message.createdAt()).isEqualTo(createdAt);
                    assertThat(message.read()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void execute_marksMessageReadWhenReadAtIsPresent() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                UUID.randomUUID(), conversationId, UUID.randomUUID(), "hi",
                LocalDateTime.now(), LocalDateTime.now());
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .assertNext(message -> assertThat(message.read()).isTrue())
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyFluxForAConversationWithNoMessages() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .verifyComplete();
    }

    @Test
    void execute_propagatesNotFoundWhenViewerIsNotAParticipant() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));
        // thenMany(...)'s argument is constructed eagerly as a plain Java expression regardless of
        // how the preceding Mono resolves, so the mock still needs a non-null return here even
        // though Reactor never actually subscribes to (executes) it once the guard errors first.
        when(messageRepository.findByConversation(any(UUID.class), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void execute_convertsPageAndSizeIntoLimitAndOffset() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 10, 20)).thenReturn(Flux.empty());

        useCase.execute(conversationId.toString(), viewerId.toString(), 2, 10).blockLast();

        verify(messageRepository).findByConversation(conversationId, 10, 20);
    }

    @Test
    void execute_clampsSizeToMax100() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 100, 0)).thenReturn(Flux.empty());

        useCase.execute(conversationId.toString(), viewerId.toString(), 0, 500).blockLast();

        verify(messageRepository).findByConversation(conversationId, 100, 0);
    }
}
