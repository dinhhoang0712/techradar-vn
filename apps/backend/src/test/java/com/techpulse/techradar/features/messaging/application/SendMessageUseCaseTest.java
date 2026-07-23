package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendMessageUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageBroadcaster messageBroadcaster;

    @Mock
    private NewMessageNotifier newMessageNotifier;

    private SendMessageUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private void createUseCase() {
        useCase = new SendMessageUseCase(conversationAccessGuard, conversationRepository, messageRepository,
                messageBroadcaster, newMessageNotifier);
    }

    @Test
    void execute_rejectsNullContent() {
        createUseCase();

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_CONTENT.name());
                })
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageBroadcaster, newMessageNotifier);
    }

    @Test
    void execute_rejectsBlankWhitespaceOnlyContent() {
        createUseCase();

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "   \n\t "))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadRequestException.class))
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository);
    }

    @Test
    void execute_rejectsContentLongerThan2000Chars() {
        createUseCase();
        String tooLong = "a".repeat(2001);

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), tooLong))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_CONTENT.name());
                })
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository);
    }

    @Test
    void execute_acceptsContentAtExactly2000CharLimit() {
        createUseCase();
        String maxLength = "a".repeat(2000);
        when(conversationAccessGuard.requireParticipant(eq(conversationId), eq(senderId))).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, maxLength, createdAt, null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq(maxLength), any(LocalDateTime.class)))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(eq(conversationId), eq(otherId), eq(senderId), eq(maxLength))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), maxLength))
                .assertNext(message -> assertThat(message.content()).isEqualTo(maxLength))
                .verifyComplete();
    }

    @Test
    void execute_propagatesForbiddenAndNeverBroadcastsOrNotifiesWhenSenderIsNotAParticipant() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));
        // .then(...)'s argument is constructed eagerly as a plain Java expression regardless of how
        // the preceding Mono resolves, so the mock still needs a non-null return here even though
        // Reactor never actually subscribes to (executes) it once the guard errors first — the
        // nested broadcast/notify calls, in contrast, sit behind a .flatMap lambda that genuinely
        // never runs, so those two mocks are still safe to assert zero interactions on.
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), anyString(), any(LocalDateTime.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hello"))
                .expectError(NotFoundException.class)
                .verify();

        verifyNoInteractions(messageBroadcaster, newMessageNotifier);
    }

    @Test
    void execute_persistsTrimmedContentBroadcastsAndNotifiesTheOtherParticipant() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, "hello", createdAt, null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq("hello"), any(LocalDateTime.class)))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(conversationId, otherId, senderId, "hello")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "  hello  "))
                .assertNext(message -> {
                    assertThat(message.id()).isEqualTo(messageId.toString());
                    assertThat(message.conversationId()).isEqualTo(conversationId.toString());
                    assertThat(message.senderId()).isEqualTo(senderId.toString());
                    assertThat(message.content()).isEqualTo("hello");
                    assertThat(message.read()).isFalse();
                })
                .verifyComplete();

        ArgumentCaptor<DirectMessage> broadcastCaptor = ArgumentCaptor.forClass(DirectMessage.class);
        verify(messageBroadcaster).publish(eq(otherId.toString()), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().id()).isEqualTo(messageId.toString());
        verify(newMessageNotifier).notify(conversationId, otherId, senderId, "hello");
    }

    @Test
    void execute_stillReturnsTheSentMessageWhenNotificationCreationFails() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, "hello", LocalDateTime.now(), null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), anyString(), any(LocalDateTime.class)))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(conversationId, otherId, senderId, "hello"))
                .thenReturn(Mono.error(new RuntimeException("notification service down")));

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hello"))
                .assertNext(message -> assertThat(message.id()).isEqualTo(messageId.toString()))
                .verifyComplete();

        verify(messageBroadcaster).publish(eq(otherId.toString()), any(DirectMessage.class));
    }
}
