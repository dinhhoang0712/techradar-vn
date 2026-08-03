package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.features.messaging.realtime.MessageLiveEvent;
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
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private static MessageRepository.MessageRow row(UUID id, UUID conversationId, UUID senderId, String content,
                                                      LocalDateTime createdAt, LocalDateTime readAt) {
        return new MessageRepository.MessageRow(id, conversationId, senderId, content, createdAt, readAt, null, null, null);
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
        MessageRepository.MessageRow row = row(messageId, conversationId, senderId, maxLength, createdAt, null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq(maxLength), any(LocalDateTime.class), isNull()))
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
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), anyString(), any(LocalDateTime.class), isNull()))
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
        MessageRepository.MessageRow row = row(messageId, conversationId, senderId, "hello", createdAt, null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq("hello"), any(LocalDateTime.class), isNull()))
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

        ArgumentCaptor<MessageLiveEvent> broadcastCaptor = ArgumentCaptor.forClass(MessageLiveEvent.class);
        verify(messageBroadcaster).publish(eq(otherId.toString()), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().type()).isEqualTo(MessageLiveEvent.Type.NEW_MESSAGE);
        assertThat(broadcastCaptor.getValue().message().id()).isEqualTo(messageId.toString());
        verify(newMessageNotifier).notify(conversationId, otherId, senderId, "hello");
    }

    @Test
    void execute_stillReturnsTheSentMessageWhenNotificationCreationFails() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        MessageRepository.MessageRow row = row(messageId, conversationId, senderId, "hello", LocalDateTime.now(), null);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), anyString(), any(LocalDateTime.class), isNull()))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(conversationId, otherId, senderId, "hello"))
                .thenReturn(Mono.error(new RuntimeException("notification service down")));

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hello"))
                .assertNext(message -> assertThat(message.id()).isEqualTo(messageId.toString()))
                .verifyComplete();

        verify(messageBroadcaster).publish(eq(otherId.toString()), any(MessageLiveEvent.class));
    }

    // ---- attachment handling ------------------------------------------------

    private static SendMessageUseCase.AttachmentPayload validAttachmentPayload() {
        String base64 = Base64.getEncoder().encodeToString("hello world".getBytes());
        return new SendMessageUseCase.AttachmentPayload("image/png", "photo.png", base64);
    }

    @Test
    void execute_acceptsAnAttachmentOnlyMessageWithEmptyContent() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, "", LocalDateTime.now(), null, "image/png", "photo.png", 11);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq(""), any(LocalDateTime.class),
                any(MessageRepository.AttachmentInput.class)))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(eq(conversationId), eq(otherId), eq(senderId), eq(""))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "", validAttachmentPayload()))
                .assertNext(message -> {
                    assertThat(message.attachment()).isNotNull();
                    assertThat(message.attachment().contentType()).isEqualTo("image/png");
                    assertThat(message.attachment().filename()).isEqualTo("photo.png");
                    assertThat(message.attachment().size()).isEqualTo(11);
                })
                .verifyComplete();
    }

    @Test
    void execute_rejectsEmptyContentAndNoAttachment() {
        createUseCase();

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_CONTENT.name());
                })
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsInvalidBase64AttachmentWithoutTouchingTheRepository() {
        createUseCase();
        SendMessageUseCase.AttachmentPayload invalid =
                new SendMessageUseCase.AttachmentPayload("image/png", "photo.png", "not-valid-base64!!");

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hi", invalid))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadRequestException.class))
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsUnsupportedAttachmentContentType() {
        createUseCase();
        String base64 = Base64.getEncoder().encodeToString("<script/>".getBytes());
        SendMessageUseCase.AttachmentPayload svg = new SendMessageUseCase.AttachmentPayload("image/svg+xml", "evil.svg", base64);

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hi", svg))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadRequestException.class))
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageBroadcaster);
    }

    @Test
    void execute_rejectsOversizedAttachment() {
        createUseCase();
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        SendMessageUseCase.AttachmentPayload oversized =
                new SendMessageUseCase.AttachmentPayload("image/png", "big.png", Base64.getEncoder().encodeToString(tooBig));

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "hi", oversized))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadRequestException.class))
                .verify();

        verifyNoInteractions(conversationAccessGuard, messageRepository, messageBroadcaster);
    }

    @Test
    void execute_passesAttachmentToRepositoryAlongsideNonEmptyContent() {
        createUseCase();
        when(conversationAccessGuard.requireParticipant(conversationId, senderId)).thenReturn(Mono.empty());
        UUID messageId = UUID.randomUUID();
        MessageRepository.MessageRow row = new MessageRepository.MessageRow(
                messageId, conversationId, senderId, "check this out", LocalDateTime.now(), null, "image/png", "photo.png", 11);
        ArgumentCaptor<MessageRepository.AttachmentInput> attachmentCaptor = ArgumentCaptor.forClass(MessageRepository.AttachmentInput.class);
        when(messageRepository.insert(any(UUID.class), eq(conversationId), eq(senderId), eq("check this out"), any(LocalDateTime.class),
                attachmentCaptor.capture()))
                .thenReturn(Mono.just(row));
        when(conversationRepository.otherParticipant(conversationId, senderId)).thenReturn(Mono.just(otherId));
        when(newMessageNotifier.notify(eq(conversationId), eq(otherId), eq(senderId), eq("check this out"))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), senderId.toString(), "check this out", validAttachmentPayload()))
                .assertNext(message -> assertThat(message.id()).isEqualTo(messageId.toString()))
                .verifyComplete();

        assertThat(attachmentCaptor.getValue().contentType()).isEqualTo("image/png");
        assertThat(attachmentCaptor.getValue().filename()).isEqualTo("photo.png");
        assertThat(new String(attachmentCaptor.getValue().data())).isEqualTo("hello world");
    }
}
