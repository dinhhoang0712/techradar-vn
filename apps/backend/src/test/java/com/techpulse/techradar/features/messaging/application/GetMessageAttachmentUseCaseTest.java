package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMessageAttachmentUseCaseTest {

    @Mock
    private ConversationAccessGuard conversationAccessGuard;
    @Mock
    private MessageRepository messageRepository;

    private GetMessageAttachmentUseCase useCase;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetMessageAttachmentUseCase(conversationAccessGuard, messageRepository);
    }

    private MessageRepository.MessageRow messageRow(UUID conversationId) {
        return new MessageRepository.MessageRow(messageId, conversationId, UUID.randomUUID(), "hi",
                LocalDateTime.now(), null, "image/png", "photo.png", 3);
    }

    @Test
    void execute_rejectsWhenViewerIsNotAParticipant() {
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId))
                .thenReturn(Mono.error(new NotFoundException("Conversation not found: " + conversationId)));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), viewerId.toString()))
                .expectError(NotFoundException.class)
                .verify();

        verify(messageRepository, never()).findAttachmentData(any());
    }

    @Test
    void execute_rejectsWhenTheMessageBelongsToADifferentConversation() {
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        UUID otherConversationId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow(otherConversationId)));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), viewerId.toString()))
                .expectError(NotFoundException.class)
                .verify();

        verify(messageRepository, never()).findAttachmentData(any());
    }

    @Test
    void execute_rejectsWhenTheMessageHasNoAttachment() {
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow(conversationId)));
        when(messageRepository.findAttachmentData(messageId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), viewerId.toString()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void execute_returnsTheAttachmentBytesForAParticipant() {
        when(conversationAccessGuard.requireParticipant(conversationId, viewerId)).thenReturn(Mono.empty());
        when(messageRepository.findById(messageId)).thenReturn(Mono.just(messageRow(conversationId)));
        MessageRepository.AttachmentRow attachmentRow = new MessageRepository.AttachmentRow("image/png", "photo.png", new byte[]{1, 2, 3});
        when(messageRepository.findAttachmentData(messageId)).thenReturn(Mono.just(attachmentRow));

        StepVerifier.create(useCase.execute(conversationId.toString(), messageId.toString(), viewerId.toString()))
                .assertNext(att -> {
                    assertThat(att.contentType()).isEqualTo("image/png");
                    assertThat(att.filename()).isEqualTo("photo.png");
                    assertThat(att.data()).containsExactly(1, 2, 3);
                })
                .verifyComplete();
    }
}
