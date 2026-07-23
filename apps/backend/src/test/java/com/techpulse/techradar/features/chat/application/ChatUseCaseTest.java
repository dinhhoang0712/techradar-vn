package com.techpulse.techradar.features.chat.application;

import com.techpulse.techradar.features.chat.domain.ChatHealthResponse;
import com.techpulse.techradar.features.chat.domain.ChatMessageItem;
import com.techpulse.techradar.features.chat.domain.ChatRequest;
import com.techpulse.techradar.features.chat.domain.ChatResponse;
import com.techpulse.techradar.features.chat.domain.ChatSessionItem;
import com.techpulse.techradar.features.chat.domain.CreateSessionResponse;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.chat.ports.ChatRepository;
import com.techpulse.techradar.shared.exception.ForbiddenException;
import com.techpulse.techradar.shared.exception.RateLimitExceededException;
import com.techpulse.techradar.shared.redis.ChatRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUseCaseTest {

    @Mock
    private ChatPort chatPort;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatRateLimiterService rateLimiter;

    private ChatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChatUseCase(chatPort, chatRepository, rateLimiter);
    }

    @Test
    void getHealth_delegatesToChatPort() {
        ChatHealthResponse health = new ChatHealthResponse("ok", true, "1.0");
        when(chatPort.getHealth()).thenReturn(Mono.just(health));

        StepVerifier.create(useCase.getHealth()).expectNext(health).verifyComplete();
    }

    @Test
    void createSession_anonymousUser_doesNotPersistToRepository() {
        StepVerifier.create(useCase.createSession(null))
                .assertNext(r -> assertThat(UUID.fromString(r.getSessionId())).isNotNull())
                .verifyComplete();

        verify(chatRepository, never()).saveSession(any());
    }

    @Test
    void createSession_authenticatedUser_persistsSession() {
        String userId = UUID.randomUUID().toString();
        when(chatRepository.saveSession(any(ChatSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.createSession(userId))
                .assertNext(r -> assertThat(r.getSessionId()).isNotBlank())
                .verifyComplete();

        verify(chatRepository).saveSession(any(ChatSession.class));
    }

    @Test
    void chat_blockedByRateLimit_neverCompletesWithAChatPortResponse() {
        ChatRequest request = new ChatRequest("hello", null, "user-1");
        when(rateLimiter.isAllowed("user-1")).thenReturn(Mono.just(false));
        // .then(chatPort.chat(request)) evaluates this Java call eagerly to build the combinator
        // argument, but Reactor only subscribes to it once the rate-limit check completes — which
        // it never does here — so this stub proves it was never actually reached/subscribed.
        when(chatPort.chat(request)).thenReturn(Mono.just(new ChatResponse("unexpected", "sid", null, null, null, "hello")));

        StepVerifier.create(useCase.chat(request))
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    void chat_anonymousRequest_skipsRateLimitAndCallsPort() {
        ChatRequest request = new ChatRequest("hello", null, null);
        ChatResponse response = new ChatResponse("answer", "sid", null, null, null, "hello");
        when(chatPort.chat(request)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.chat(request)).expectNext(response).verifyComplete();

        verify(rateLimiter, never()).isAllowed(any());
        verify(chatRepository, never()).saveSession(any());
    }

    @Test
    void chat_forbiddenWhenSessionOwnedByAnotherUser() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String attackerId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest("hello", sessionId.toString(), attackerId);
        ChatSession existing = ChatSession.builder().id(sessionId).userId(ownerId).build();

        when(rateLimiter.isAllowed(attackerId)).thenReturn(Mono.just(true));
        when(chatRepository.findSessionById(sessionId.toString())).thenReturn(Mono.just(existing));
        // initializeSession's switchIfEmpty(createChatSession(...)) fallback is built eagerly (it's
        // a plain Java argument, not deferred), so createChatSession's own saveSession(...) call
        // fires too even though this fallback branch is never actually reached at runtime.
        when(chatRepository.saveSession(any())).thenReturn(Mono.just(existing));
        // Eagerly-evaluated .then(...) argument, never actually subscribed (see comment above).
        when(chatPort.chat(request)).thenReturn(Mono.just(new ChatResponse("unexpected", "sid", null, null, null, "hello")));

        StepVerifier.create(useCase.chat(request)).expectError(ForbiddenException.class).verify();
    }

    @Test
    void listSessions_delegatesToRepository() {
        ChatSessionItem item = new ChatSessionItem(UUID.randomUUID(), "title", Instant.now());
        when(chatRepository.listSessionsByUser("user-1")).thenReturn(Flux.just(item));

        StepVerifier.create(useCase.listSessions("user-1")).expectNext(item).verifyComplete();
    }

    @Test
    void deleteSession_ownerDeletesSuccessfully() {
        String userId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        ChatSession existing = ChatSession.builder().id(UUID.fromString(sessionId)).userId(UUID.fromString(userId)).build();

        when(chatRepository.findSessionById(sessionId)).thenReturn(Mono.just(existing));
        when(chatRepository.deleteSession(sessionId)).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.deleteSession(sessionId, userId)).verifyComplete();

        verify(chatRepository).deleteSession(sessionId);
    }

    @Test
    void deleteSession_forbiddenWhenNotOwner_neverDeletes() {
        String sessionId = UUID.randomUUID().toString();
        ChatSession existing = ChatSession.builder().id(UUID.fromString(sessionId)).userId(UUID.randomUUID()).build();

        when(chatRepository.findSessionById(sessionId)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.deleteSession(sessionId, UUID.randomUUID().toString()))
                .expectError(ForbiddenException.class)
                .verify();

        verify(chatRepository, never()).deleteSession(any());
    }

    @Test
    void deleteSession_noSessionRow_completesWithoutError() {
        String sessionId = UUID.randomUUID().toString();
        when(chatRepository.findSessionById(sessionId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteSession(sessionId, UUID.randomUUID().toString())).verifyComplete();

        verify(chatRepository, never()).deleteSession(any());
    }

    @Test
    void listMessages_ownerAllowed_returnsHistory() {
        String userId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        ChatSession existing = ChatSession.builder().id(UUID.fromString(sessionId)).userId(UUID.fromString(userId)).build();
        ChatMessageItem message = new ChatMessageItem(UUID.randomUUID(), "user", "hi");

        when(chatRepository.findSessionById(sessionId)).thenReturn(Mono.just(existing));
        when(chatRepository.listMessages(sessionId)).thenReturn(Flux.just(message));

        StepVerifier.create(useCase.listMessages(sessionId, userId)).expectNext(message).verifyComplete();
    }

    @Test
    void listMessages_forbiddenWhenNotOwner() {
        String sessionId = UUID.randomUUID().toString();
        ChatSession existing = ChatSession.builder().id(UUID.fromString(sessionId)).userId(UUID.randomUUID()).build();

        when(chatRepository.findSessionById(sessionId)).thenReturn(Mono.just(existing));
        // listMessages(sessionId) is also called eagerly as the switchIfEmpty(...) fallback
        // argument at assembly time, regardless of the owner check outcome below.
        when(chatRepository.listMessages(sessionId)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.listMessages(sessionId, UUID.randomUUID().toString()))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void streamChat_blockedByRateLimit_neverCompletesWithAChatPortStream() {
        ChatRequest request = new ChatRequest("hello", null, "user-1");
        when(rateLimiter.isAllowed("user-1")).thenReturn(Mono.just(false));
        // Eagerly-evaluated .thenMany(...) argument, never actually subscribed (see comment above).
        when(chatPort.streamChat(request)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.streamChat(request))
                .expectError(RateLimitExceededException.class)
                .verify();
    }
}
