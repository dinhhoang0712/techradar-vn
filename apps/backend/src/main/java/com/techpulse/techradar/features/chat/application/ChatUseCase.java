package com.techpulse.techradar.features.chat.application;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.CreateSessionResponse;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatSessionItem;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.chat.ports.ChatRepository;
import com.techpulse.techradar.shared.exception.ForbiddenException;
import com.techpulse.techradar.shared.exception.RateLimitExceededException;
import com.techpulse.techradar.shared.redis.ChatRateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat use case orchestration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUseCase {

    private final ChatPort chatPort;
    private final ChatRepository chatRepository;
    private final ChatRateLimiterService rateLimiter;

    public Mono<ChatHealthResponse> getHealth() {
        return chatPort.getHealth();
    }

    public Mono<CreateSessionResponse> createSession(String userId) {
        log.info("Creating chat session for userId={}", userId);
        return initializeSession(null, userId)
                .map(session -> new CreateSessionResponse(session.getId().toString(), session.getCreatedAt()))
                .doOnSuccess(r -> log.info("Created chat session sessionId={} for userId={}", r.getSessionId(), userId));
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        // Backend owns the session lifecycle (create + auth); chat MESSAGES
        // (user + assistant) are persisted by ai-rag-core to avoid double-writes.
        // See apps/backend/src/main/resources/db/README.md.
        log.info("Handling chat request sessionId={} userId={}", request.getSessionId(), request.getUserId());
        return checkRateLimit(request.getUserId())
                .then(initializeSession(request.getSessionId(), request.getUserId()))
                .then(chatPort.chat(request))
                .doOnSuccess(r -> log.info("Completed chat request sessionId={} userId={}",
                        request.getSessionId(), request.getUserId()));
    }

    public Flux<ChatSessionItem> listSessions(String userId) {
        log.info("Listing chat sessions for userId={}", userId);
        return chatRepository.listSessionsByUser(userId);
    }

    public Mono<Void> deleteSession(String sessionId, String userId) {
        log.info("Deleting chat session sessionId={} userId={}", sessionId, userId);
        return chatRepository.findSessionById(sessionId)
                .flatMap(session -> {
                    if (!isOwner(session, userId)) {
                        log.warn("Forbidden delete: userId={} is not owner of sessionId={}", userId, sessionId);
                        return Mono.error(new ForbiddenException("You do not have access to this chat session"));
                    }
                    return chatRepository.deleteSession(sessionId);
                })
                // No persisted session (anonymous/in-memory) -> nothing to delete.
                .switchIfEmpty(Mono.just(0L))
                .then();
    }

    public Flux<ChatMessageItem> listMessages(String sessionId, String userId) {
        log.info("Listing messages for sessionId={} userId={}", sessionId, userId);
        return chatRepository.findSessionById(sessionId)
                .flatMapMany(session -> {
                    if (!isOwner(session, userId)) {
                        log.warn("Forbidden message access: userId={} is not owner of sessionId={}", userId, sessionId);
                        return Flux.error(new ForbiddenException("You do not have access to this chat session"));
                    }
                    return chatRepository.listMessages(sessionId);
                })
                // Session row may not exist yet (e.g. anonymous/in-memory session) -> no history.
                .switchIfEmpty(chatRepository.listMessages(sessionId));
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        // Same as chat(): ensure the session exists, then stream. ai-rag-core
        // persists the user + assistant messages (it accumulates the streamed
        // answer), so the gateway does not write them here.
        log.info("Streaming chat request sessionId={} userId={}", request.getSessionId(), request.getUserId());
        return checkRateLimit(request.getUserId())
                .thenMany(initializeSession(request.getSessionId(), request.getUserId()))
                .thenMany(chatPort.streamChat(request));
    }

    private Mono<Void> checkRateLimit(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.empty();
        }
        return rateLimiter.isAllowed(userId)
                .flatMap(allowed -> allowed
                        ? Mono.empty()
                        : Mono.error(new RateLimitExceededException("Chat rate limit exceeded. Please slow down.")));
    }

    private Mono<ChatSession> initializeSession(String sessionId, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return chatRepository.findSessionById(sessionId)
                    .flatMap(session -> isOwner(session, userId)
                            ? Mono.just(session)
                            : Mono.error(new ForbiddenException("You do not have access to this chat session")))
                    .switchIfEmpty(createChatSession(sessionId, userId));
        }
        return createChatSession(null, userId);
    }

    /**
     * A session is accessible when it has no owner (anonymous) or its owner matches the caller.
     */
    private boolean isOwner(ChatSession session, String userId) {
        if (session.getUserId() == null) {
            return true;
        }
        return userId != null && session.getUserId().toString().equals(userId);
    }

    private Mono<ChatSession> createChatSession(String sessionId, String userId) {
        UUID id = sessionId != null && !sessionId.isBlank() ? UUID.fromString(sessionId) : UUID.randomUUID();
        ChatSession session = ChatSession.builder()
                .id(id)
                .userId(isValidUuid(userId) ? UUID.fromString(userId) : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        if (session.getUserId() == null) {
            return Mono.just(session);
        }
        return chatRepository.saveSession(session);
    }

    private boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
