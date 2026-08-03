package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.features.messaging.realtime.MessageLiveEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoveMessageReactionUseCaseTest {

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

    private RemoveMessageReactionUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new RemoveMessageReactionUseCase(conversationAccessGuard, conversationRepository, messageRepository,
                messageReactionRepository, messageBroadcaster);
    }

    private MessageRepository.MessageRow messageRow() {
        return new MessageRepository.MessageRow(messageId, conversationId, UUID.randomUUID(), "hi",
                LocalDateTime.now(), null, null, null, null);
    }

    @Test
    void execute_rejectsWhenTheMessageBelongsToADifferentConversation() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        UUID otherConversationId = UUID.randomUUID();
        MessageRepository.MessageRow rowInOtherConversation = new MessageRepository.MessageRow(
                messageId, otherConversationId, UUID.randomUUID(), "hi", LocalDateTime.now(), null, null, null, null);
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(rowInOtherConversation));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString()))
                .expectError(NotFoundException.class)
                .verify();

        verifyNoInteractions(messageReactionRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsWhenViewerIsNotAParticipant() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString()))
                .expectError(NotFoundException.class)
                .verify();

        verifyNoInteractions(messageRepository, messageReactionRepository, messageBroadcaster);
    }

    @Test
    void execute_removesAndReturnsTheRemainingReactionsFromTheActorsPerspective() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.remove(messageId, userId)).thenReturn(Mono.empty());
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.empty());
        when(conversationRepository.otherParticipant(conversationId, userId)).thenReturn(Mono.just(otherId));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString()))
                .assertNext(summaries -> assertThat(summaries).isEmpty())
                .verifyComplete();

        verify(messageReactionRepository).remove(messageId, userId);
    }

    @Test
    void execute_broadcastsTheReactionChangeToTheOtherParticipant() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.remove(messageId, userId)).thenReturn(Mono.empty());
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.just(
                new MessageReactionRepository.ReactionRow(messageId, otherId, "😮")));
        when(conversationRepository.otherParticipant(conversationId, userId)).thenReturn(Mono.just(otherId));

        useCase.execute(conversationId.toString(), messageId.toString(), userId.toString()).block();

        ArgumentCaptor<MessageLiveEvent> captor = ArgumentCaptor.forClass(MessageLiveEvent.class);
        verify(messageBroadcaster).publish(eq(otherId.toString()), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(MessageLiveEvent.Type.REACTIONS_CHANGED);
        assertThat(captor.getValue().reactions()).singleElement().satisfies(r -> {
            assertThat(r.emoji()).isEqualTo("😮");
            assertThat(r.reactedByMe()).isTrue();
        });
    }

    @Test
    void execute_stillSucceedsWhenBroadcastingFails() {
        when(conversationAccessGuard.requireParticipant(conversationId, userId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow()));
        when(messageReactionRepository.remove(messageId, userId)).thenReturn(Mono.empty());
        when(messageReactionRepository.findByMessageId(messageId)).thenReturn(Flux.empty());
        when(conversationRepository.otherParticipant(conversationId, userId))
                .thenReturn(Mono.error(new RuntimeException("lookup failed")));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), userId.toString()))
                .assertNext(summaries -> assertThat(summaries).isEmpty())
                .verifyComplete();
    }
}
