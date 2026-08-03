package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.features.messaging.realtime.MessageLiveEvent;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetMessageReactionUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;
    @Mock
    private MessageBroadcaster messageBroadcaster;

    private SetMessageReactionUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new SetMessageReactionUseCase(conversationAccessGuard, conversationRepository, messageRepository,
                messageReactionRepository, messageBroadcaster);
    }

    private MessageRepository.MessageRow messageRow() {
        return new MessageRepository.MessageRow(messageId, conversationId, UUID.randomUUID(), "hi",
                LocalDateTime.now(), null, null, null, null);
    }

    @Test
    void execute_rejectsAnEmojiOutsideTheAllowedSet() {
        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "🍕"))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadRequestException.class))
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageReactionRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsWhenTheMessageBelongsToADifferentConversation() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        UUID otherConversationId = UUID.randomUUID();
        MessageRepository.MessageRow rowInOtherConversation = new MessageRepository.MessageRow(
                messageId, otherConversationId, UUID.randomUUID(), "hi", LocalDateTime.now(), null, null, null, null);
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(rowInOtherConversation));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "👍"))
                .expectError(NotFoundException.class)
                .verify();

        verifyNoInteractions(messageReactionRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsWhenViewerIsNotAParticipant() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "👍"))
                .expectError(NotFoundException.class)
                .verify();

        verifyNoInteractions(messageRepository, messageReactionRepository, messageBroadcaster);
    }

    @Test
    void execute_upsertsAndReturnsTheActorsOwnPerspective() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.upsert(messageId, userId, "👍")).thenReturn(Mono.empty());
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.just(
                new MessageReactionRepository.ReactionRow(messageId, userId, "👍")));
        when(conversationRepository.otherParticipant(conversationId, userId)).thenReturn(Mono.just(otherId));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "👍"))
                .assertNext(summaries -> {
                    assertThat(summaries).hasSize(1);
                    MessageReactionSummary summary = summaries.get(0);
                    assertThat(summary.emoji()).isEqualTo("👍");
                    assertThat(summary.count()).isEqualTo(1);
                    assertThat(summary.reactedByMe()).isTrue();
                })
                .verifyComplete();

        verify(messageReactionRepository).upsert(messageId, userId, "👍");
    }

    @Test
    void execute_broadcastsTheReactionChangeFromTheOtherParticipantsPerspective() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.upsert(messageId, userId, "❤️")).thenReturn(Mono.empty());
        // Only the acting user has reacted so far — from the OTHER participant's perspective,
        // reactedByMe must be false even though the summary the actor gets back has it true.
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.just(
                new MessageReactionRepository.ReactionRow(messageId, userId, "❤️")));
        when(conversationRepository.otherParticipant(conversationId, userId)).thenReturn(Mono.just(otherId));

        useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "❤️").block();

        ArgumentCaptor<MessageLiveEvent> captor = ArgumentCaptor.forClass(MessageLiveEvent.class);
        verify(messageBroadcaster).publish(eq(otherId.toString()), captor.capture());
        MessageLiveEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(MessageLiveEvent.Type.REACTIONS_CHANGED);
        assertThat(event.conversationId()).isEqualTo(conversationId.toString());
        assertThat(event.messageId()).isEqualTo(messageId.toString());
        assertThat(event.reactions()).singleElement().satisfies(r -> {
            assertThat(r.emoji()).isEqualTo("❤️");
            assertThat(r.reactedByMe()).isFalse();
        });
    }

    @Test
    void execute_stillSucceedsWhenBroadcastingFails() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.upsert(messageId, userId, "👍")).thenReturn(Mono.empty());
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.just(
                new MessageReactionRepository.ReactionRow(messageId, userId, "👍")));
        when(conversationRepository.otherParticipant(conversationId, userId))
                .thenReturn(Mono.error(new RuntimeException("lookup failed")));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "👍"))
                .assertNext(summaries -> assertThat(summaries).hasSize(1))
                .verifyComplete();
    }

    @Test
    void execute_neverTouchesReactionRepositoryWhenEmojiIsInvalid() {
        useCase.execute(conversationId.toString(), messageId.toString(), userId.toString(), "not-an-emoji").subscribe(v -> { }, e -> { });

        verify(messageReactionRepository, never()).upsert(any(), any(), any());
    }
}
