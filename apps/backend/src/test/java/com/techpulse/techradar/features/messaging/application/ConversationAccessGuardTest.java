package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationAccessGuardTest {

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationAccessGuard guard;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void requireParticipant_completesWhenUserIsAParticipant() {
        guard = new ConversationAccessGuard(conversationRepository);
        when(conversationRepository.isParticipant(conversationId, userId)).thenReturn(Mono.just(true));

        StepVerifier.create(guard.requireParticipant(conversationId, userId))
                .verifyComplete();

        verify(conversationRepository).isParticipant(conversationId, userId);
    }

    @Test
    void requireParticipant_errorsWithNotFoundWhenUserIsNotAParticipant() {
        guard = new ConversationAccessGuard(conversationRepository);
        when(conversationRepository.isParticipant(conversationId, userId)).thenReturn(Mono.just(false));

        StepVerifier.create(guard.requireParticipant(conversationId, userId))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).contains(conversationId.toString());
                })
                .verify();
    }
}
