package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.features.messaging.realtime.MessageLiveEvent;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetMessageReactionUseCase {

    // A small, fixed Messenger-style palette rather than an arbitrary emoji picker — keeps the
    // aggregated-count rendering predictable and avoids validating arbitrary unicode as "an emoji".
    public static final Set<String> ALLOWED_EMOJI = Set.of("👍", "❤️", "😂", "😮", "😢", "😡");

    private final ConversationAccessGuard conversationAccessGuard;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageBroadcaster messageBroadcaster;

    public Mono<List<MessageReactionSummary>> execute(String conversationId, String messageId, String userId, String emoji) {
        if (!ALLOWED_EMOJI.contains(emoji)) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_CONTENT, "Unsupported reaction emoji"));
        }

        UUID convId = UUID.fromString(conversationId);
        UUID msgId = UUID.fromString(messageId);
        UUID userUuid = UUID.fromString(userId);

        return conversationAccessGuard.requireParticipant(convId, userUuid)
                .then(Mono.defer(() -> messageRepository.findById(msgId)))
                .filter(row -> row.conversationId().equals(convId))
                .switchIfEmpty(Mono.error(new NotFoundException("Message not found: " + messageId)))
                .then(Mono.defer(() -> messageReactionRepository.upsert(msgId, userUuid, emoji)))
                .then(Mono.defer(() -> messageReactionRepository.findByMessageId(msgId).collectList()))
                .flatMap(rows -> broadcastToOtherParticipant(convId, messageId, rows, userUuid)
                        .thenReturn(ReactionSummaries.summarize(rows, userUuid)));
    }

    private Mono<Void> broadcastToOtherParticipant(UUID conversationId, String messageId,
                                                     List<MessageReactionRepository.ReactionRow> rows, UUID actingUserId) {
        return conversationRepository.otherParticipant(conversationId, actingUserId)
                .doOnNext(otherId -> messageBroadcaster.publish(otherId.toString(),
                        MessageLiveEvent.reactionsChanged(conversationId.toString(), messageId,
                                ReactionSummaries.summarize(rows, otherId))))
                .onErrorResume(e -> {
                    log.warn("Could not broadcast reaction change for message {}", messageId, e);
                    return Mono.empty();
                })
                .then();
    }
}
