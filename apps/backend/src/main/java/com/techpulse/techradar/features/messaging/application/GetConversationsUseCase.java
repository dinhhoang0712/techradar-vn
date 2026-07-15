package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.ConversationSummary;
import com.techpulse.techradar.features.messaging.domain.UserRef;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** A user's conversations, most recently active first. */
@Component
@RequiredArgsConstructor
public class GetConversationsUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ConversationRepository conversationRepository;

    public Flux<ConversationSummary> execute(String userId, int page, int size) {
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;

        return conversationRepository.findAllForUser(UUID.fromString(userId), effectiveSize, offset)
                .map(row -> new ConversationSummary(
                        row.id().toString(),
                        new UserRef(row.otherUserId().toString(), row.otherUserName(), row.otherUserAvatarUrl()),
                        row.lastMessageContent(),
                        row.lastMessageAt(),
                        row.lastMessageSenderId() != null ? row.lastMessageSenderId().toString() : null,
                        row.unreadCount()
                ));
    }
}
