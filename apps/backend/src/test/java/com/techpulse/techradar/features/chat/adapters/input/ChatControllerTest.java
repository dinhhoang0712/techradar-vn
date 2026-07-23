package com.techpulse.techradar.features.chat.adapters.input;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageRequest;
import com.techpulse.techradar.features.chat.application.ChatUseCase;
import com.techpulse.techradar.features.chat.domain.ChatHealthResponse;
import com.techpulse.techradar.features.chat.domain.ChatMessageItem;
import com.techpulse.techradar.features.chat.domain.ChatRequest;
import com.techpulse.techradar.features.chat.domain.ChatResponse;
import com.techpulse.techradar.features.chat.domain.ChatSessionItem;
import com.techpulse.techradar.features.chat.domain.CreateSessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatUseCase chatUseCase;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatUseCase);
    }

    private static reactor.util.context.Context authenticatedAs(String userId) {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void health_returnsChatUseCaseHealth() {
        ChatHealthResponse health = new ChatHealthResponse("ok", true, "1.0");
        when(chatUseCase.getHealth()).thenReturn(Mono.just(health));

        StepVerifier.create(controller.health())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEqualTo(health);
                })
                .verifyComplete();
    }

    @Test
    void createSession_authenticatedUser_delegatesWithUserId() {
        CreateSessionResponse sessionResponse = new CreateSessionResponse(UUID.randomUUID().toString(), Instant.now());
        when(chatUseCase.createSession("user-1")).thenReturn(Mono.just(sessionResponse));

        StepVerifier.create(controller.createSession().contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEqualTo(sessionResponse);
                })
                .verifyComplete();
    }

    @Test
    void createSession_anonymous_neverCallsUseCase() {
        StepVerifier.create(controller.createSession()).verifyComplete();

        verify(chatUseCase, org.mockito.Mockito.never()).createSession(any());
    }

    @Test
    void listSessions_returnsSessionsForCurrentUser() {
        ChatSessionItem item = new ChatSessionItem(UUID.randomUUID(), "title", Instant.now());
        when(chatUseCase.listSessions("user-1")).thenReturn(Flux.just(item));

        StepVerifier.create(controller.listSessions().contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).containsExactly(item);
                })
                .verifyComplete();
    }

    @Test
    void deleteSession_delegatesWithSessionIdAndUserId() {
        String sessionId = UUID.randomUUID().toString();
        when(chatUseCase.deleteSession(sessionId, "user-1")).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteSession(sessionId).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(chatUseCase).deleteSession(sessionId, "user-1");
    }

    @Test
    void listMessages_returnsHistoryForSession() {
        String sessionId = UUID.randomUUID().toString();
        ChatMessageItem message = new ChatMessageItem(UUID.randomUUID(), "user", "hi");
        when(chatUseCase.listMessages(sessionId, "user-1")).thenReturn(Flux.just(message));

        StepVerifier.create(controller.listMessages(sessionId).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> assertThat(response.getBody().getData()).containsExactly(message))
                .verifyComplete();
    }

    @Test
    void postMessage_buildsChatRequestFromPathAndBody_andReturnsResponse() {
        String sessionId = UUID.randomUUID().toString();
        ChatMessageRequest request = new ChatMessageRequest();
        request.setQuery("hello");
        ChatResponse chatResponse = new ChatResponse("answer", sessionId, null, null, null, "hello");
        when(chatUseCase.chat(new ChatRequest("hello", sessionId, "user-1"))).thenReturn(Mono.just(chatResponse));

        StepVerifier.create(controller.postMessage(sessionId, request).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEqualTo(chatResponse);
                })
                .verifyComplete();
    }

    @Test
    void postMessageStream_delegatesToStreamChat() {
        String sessionId = UUID.randomUUID().toString();
        ChatMessageRequest request = new ChatMessageRequest();
        request.setQuery("hello");
        ServerSentEvent<String> event = ServerSentEvent.builder("chunk").build();
        when(chatUseCase.streamChat(new ChatRequest("hello", sessionId, "user-1"))).thenReturn(Flux.just(event));

        StepVerifier.create(controller.postMessageStream(sessionId, request).contextWrite(authenticatedAs("user-1")))
                .expectNext(event)
                .verifyComplete();
    }
}
