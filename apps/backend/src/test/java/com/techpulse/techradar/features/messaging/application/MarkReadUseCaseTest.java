package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkReadUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;

    @Mock
    private MessageRepository messageRepository;

    private MarkReadUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();

    @Test
    void execute_marksConversationReadAfterConfirmingParticipant() {
        useCase = new MarkReadUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, readerId)).thenReturn(Mono.empty());
        when(messageRepository.markRead(conversationId, readerId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), readerId.toString()))
                .verifyComplete();

        InOrder inOrder = inOrder(conversationAccessGuard, messageRepository);
        inOrder.verify(conversationAccessGuard).requireParticipant(conversationId, readerId);
        inOrder.verify(messageRepository).markRead(conversationId, readerId);
    }

    @Test
    void execute_isIdempotentWhenEverythingIsAlreadyRead() {
        useCase = new MarkReadUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, readerId)).thenReturn(Mono.empty());
        when(messageRepository.markRead(conversationId, readerId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), readerId.toString()))
                .verifyComplete();
        StepVerifier.create(useCase.execute(conversationId.toString(), readerId.toString()))
                .verifyComplete();

        verify(messageRepository, times(2)).markRead(conversationId, readerId);
    }

    @Test
    void execute_propagatesForbiddenWhenNotAParticipant() {
        useCase = new MarkReadUseCase(conversationAccessGuard, messageRepository);
        when(conversationAccessGuard.requireParticipant(conversationId, readerId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));
        // .then(...)'s argument is constructed eagerly as a plain Java expression regardless of how
        // the preceding Mono resolves, so the mock still needs a non-null return here even though
        // Reactor never actually subscribes to (executes) it once the guard errors first.
        when(messageRepository.markRead(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), readerId.toString()))
                .expectError(NotFoundException.class)
                .verify();
    }
}
