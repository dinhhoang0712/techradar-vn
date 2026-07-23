package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrCreateConversationUseCaseTest {

    @Mock
    private ConversationRepository conversationRepository;

    private GetOrCreateConversationUseCase useCase;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @Test
    void execute_returnsExistingOrNewConversationIdAsString() {
        useCase = new GetOrCreateConversationUseCase(conversationRepository);
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findOrCreate(viewerId, otherUserId)).thenReturn(Mono.just(conversationId));

        StepVerifier.create(useCase.execute(viewerId.toString(), otherUserId.toString()))
                .assertNext(id -> assertThat(id).isEqualTo(conversationId.toString()))
                .verifyComplete();
    }

    @Test
    void execute_rejectsMessagingYourself() {
        useCase = new GetOrCreateConversationUseCase(conversationRepository);

        StepVerifier.create(useCase.execute(viewerId.toString(), viewerId.toString()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_CONVERSATION.name());
                })
                .verify();

        verifyNoInteractions(conversationRepository);
    }
}
