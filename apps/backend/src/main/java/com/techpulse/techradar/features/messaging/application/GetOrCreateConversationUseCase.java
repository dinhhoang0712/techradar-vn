package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetOrCreateConversationUseCase {

    private final ConversationRepository conversationRepository;

    public Mono<String> execute(String viewerId, String otherUserId) {
        if (viewerId.equals(otherUserId)) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_CONVERSATION, "Cannot message yourself"));
        }
        return conversationRepository.findOrCreate(UUID.fromString(viewerId), UUID.fromString(otherUserId))
                .map(UUID::toString);
    }
}
