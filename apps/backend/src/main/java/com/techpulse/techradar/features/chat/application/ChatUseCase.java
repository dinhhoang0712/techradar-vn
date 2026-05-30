package com.techpulse.techradar.features.chat.application;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.CreateSessionResponse;
import com.techpulse.techradar.features.chat.domain.ChatMessage;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.chat.ports.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat use case orchestration.
 */
@Component
@RequiredArgsConstructor
public class ChatUseCase {

    private final ChatPort chatPort;
    private final ChatRepository chatRepository;

    public Mono<ChatHealthResponse> getHealth() {
        return chatPort.getHealth();
    }

    public Mono<CreateSessionResponse> createSession(String userId) {
        return initializeSession(null, userId)
                .map(session -> new CreateSessionResponse(session.getId().toString(), session.getCreatedAt()));
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        return initializeSession(request.getSessionId(), request.getUserId())
                .flatMap(session -> {
                    ChatMessage userMessage = ChatMessage.builder()
                            .sessionId(session.getId())
                            .role("user")
                            .content(request.getQuery())
                            .createdAt(Instant.now())
                            .build();

                    return persistMessage(session, userMessage)
                            .then(chatPort.chat(request))
                            .flatMap(response -> persistMessage(session, ChatMessage.builder()
                                    .sessionId(session.getId())
                                    .role("assistant")
                                    .content(response.getAnswer())
                                    .createdAt(Instant.now())
                                    .build())
                            .thenReturn(response);
                });
    }

    public Flux<ChatMessageItem> listMessages(String sessionId) {
        return chatRepository.listMessages(sessionId);
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        return initializeSession(request.getSessionId(), request.getUserId())
                .flatMapMany(session -> {
                    ChatMessage userMessage = ChatMessage.builder()
                            .sessionId(session.getId())
                            .role("user")
                            .content(request.getQuery())
                            .createdAt(Instant.now())
                            .build();

                    return persistMessage(session, userMessage)
                            .thenMany(chatPort.streamChat(request));
                });
    }

    private Mono<ChatSession> initializeSession(String sessionId, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return chatRepository.findSessionById(sessionId)
                    .switchIfEmpty(createChatSession(sessionId, userId));
        }
        return createChatSession(null, userId);
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

    private Mono<ChatMessage> persistMessage(ChatSession session, ChatMessage message) {
        if (session.getUserId() == null) {
            return Mono.empty();
        }
        return chatRepository.saveMessage(message);
    }
}
