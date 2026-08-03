package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;
import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetMessagesUseCase {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ConversationAccessGuard conversationAccessGuard;
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;

    public Flux<DirectMessage> execute(String conversationId, String viewerId, int page, int size) {
        UUID convId = UUID.fromString(conversationId);
        UUID viewerUuid = UUID.fromString(viewerId);
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);

        return conversationAccessGuard.requireParticipant(convId, viewerUuid)
                .thenMany(messageRepository.findByConversation(convId, pageRequest.size(), pageRequest.offset())
                        .map(MessageMapper::toDomain)
                        .collectList()
                        .flatMapMany(messages -> attachReactions(messages, viewerUuid)));
    }

    private Flux<DirectMessage> attachReactions(List<DirectMessage> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return Flux.empty();
        }
        List<UUID> ids = messages.stream().map(m -> UUID.fromString(m.id())).toList();
        return messageReactionRepository.findByMessageIds(ids)
                .collectMultimap(MessageReactionRepository.ReactionRow::messageId)
                .flatMapMany(byMessage -> Flux.fromIterable(messages)
                        .map(m -> withReactions(m, byMessage, viewerId)));
    }

    private static DirectMessage withReactions(
            DirectMessage message,
            Map<UUID, Collection<MessageReactionRepository.ReactionRow>> byMessage,
            UUID viewerId
    ) {
        UUID messageId = UUID.fromString(message.id());
        List<MessageReactionSummary> reactions =
                ReactionSummaries.summarize(byMessage.getOrDefault(messageId, List.of()), viewerId);
        return new DirectMessage(
                message.id(), message.conversationId(), message.senderId(), message.content(),
                message.createdAt(), message.read(), message.attachment(), reactions);
    }
}
