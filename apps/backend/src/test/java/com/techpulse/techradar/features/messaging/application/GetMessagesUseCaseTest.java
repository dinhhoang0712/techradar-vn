package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMessagesUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    private GetMessagesUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    private void createUseCase() {
        useCase = new GetMessagesUseCase(conversationAccessGuard, messageRepository, messageReactionRepository);
        lenient().when(messageReactionRepository.findByMessageIds(any())).thenReturn(Flux.empty());
    }

    private static MessageRepository.MessageRow row(UUID id, UUID conversationId, UUID senderId, String content,
                                                      LocalDateTime createdAt, LocalDateTime readAt) {
        return new MessageRepository.MessageRow(id, conversationId, senderId, content, createdAt, readAt, null, null, null);
    }

    @Test
    void execute_returnsMessagesOldestFirstMappedToDomain() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 9, 30);
        MessageRepository.MessageRow row = row(messageId, conversationId, senderId, "Chào bạn", createdAt, null);
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .assertNext(message -> {
                    assertThat(message.id()).isEqualTo(messageId.toString());
                    assertThat(message.conversationId()).isEqualTo(conversationId.toString());
                    assertThat(message.senderId()).isEqualTo(senderId.toString());
                    assertThat(message.content()).isEqualTo("Chào bạn");
                    assertThat(message.createdAt()).isEqualTo(createdAt);
                    assertThat(message.read()).isFalse();
                    assertThat(message.reactions()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void execute_marksMessageReadWhenReadAtIsPresent() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        MessageRepository.MessageRow row = row(UUID.randomUUID(), conversationId, UUID.randomUUID(), "hi",
                LocalDateTime.now(), LocalDateTime.now());
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .assertNext(message -> assertThat(message.read()).isTrue())
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyFluxForAConversationWithNoMessages() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .verifyComplete();

        verify(messageReactionRepository, never()).findByMessageIds(any());
    }

    @Test
    void execute_propagatesNotFoundWhenViewerIsNotAParticipant() {
        createUseCase();
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
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 10, 20)).thenReturn(Flux.empty());

        useCase.execute(conversationId.toString(), viewerId.toString(), 2, 10).blockLast();

        verify(messageRepository).findByConversation(conversationId, 10, 20);
    }

    @Test
    void execute_clampsSizeToMax100() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findByConversation(conversationId, 100, 0)).thenReturn(Flux.empty());

        useCase.execute(conversationId.toString(), viewerId.toString(), 0, 500).blockLast();

        verify(messageRepository).findByConversation(conversationId, 100, 0);
    }

    @Test
    void execute_attachesAggregatedReactionsFromTheViewersPerspective() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        MessageRepository.MessageRow row = row(messageId, conversationId, UUID.randomUUID(), "hi", LocalDateTime.now(), null);
        when(messageRepository.findByConversation(conversationId, 30, 0)).thenReturn(Flux.just(row));

        UUID otherUserId = UUID.randomUUID();
        Collection<MessageReactionRepository.ReactionRow> reactionRows = List.of(
                new MessageReactionRepository.ReactionRow(messageId, viewerId, "👍"),
                new MessageReactionRepository.ReactionRow(messageId, otherUserId, "👍"),
                new MessageReactionRepository.ReactionRow(messageId, otherUserId, "❤️"));
        when(messageReactionRepository.findByMessageIds(any())).thenReturn(Flux.fromIterable(reactionRows));

        StepVerifier.create(useCase.execute(conversationId.toString(), viewerId.toString(), 0, 30))
                .assertNext(message -> {
                    assertThat(message.reactions()).hasSize(2);
                    assertThat(message.reactions()).anySatisfy(r -> {
                        assertThat(r.emoji()).isEqualTo("👍");
                        assertThat(r.count()).isEqualTo(2);
                        assertThat(r.reactedByMe()).isTrue();
                    });
                    assertThat(message.reactions()).anySatisfy(r -> {
                        assertThat(r.emoji()).isEqualTo("❤️");
                        assertThat(r.count()).isEqualTo(1);
                        assertThat(r.reactedByMe()).isFalse();
                    });
                })
                .verifyComplete();
    }
}
