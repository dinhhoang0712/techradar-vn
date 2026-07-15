package com.techpulse.techradar.features.messaging.adapters.input;

import com.techpulse.techradar.features.messaging.application.GetConversationsUseCase;
import com.techpulse.techradar.features.messaging.application.GetMessagesUseCase;
import com.techpulse.techradar.features.messaging.application.GetOrCreateConversationUseCase;
import com.techpulse.techradar.features.messaging.application.MarkReadUseCase;
import com.techpulse.techradar.features.messaging.application.SendMessageUseCase;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 1-1 direct messaging: conversations, message history, sending, read receipts, and a live
 * per-user SSE stream for real-time delivery (see {@link MessageBroadcaster}).
 */
@Tag(name = "Messaging", description = "Direct messages between users")
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final GetConversationsUseCase getConversationsUseCase;
    private final GetOrCreateConversationUseCase getOrCreateConversationUseCase;
    private final GetMessagesUseCase getMessagesUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final MarkReadUseCase markReadUseCase;
    private final MessageBroadcaster messageBroadcaster;

    @Operation(summary = "List the current user's conversations, most recently active first")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<MessagingDtos.ConversationResponse>>>> list() {
        return SecurityUtils.currentUserId()
                .flatMapMany(getConversationsUseCase::execute)
                .map(MessagingDtos.ConversationResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Conversations")));
    }

    @Operation(summary = "Get or create the 1-1 conversation with a given user")
    @PostMapping("/with/{userId}")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> getOrCreate(@PathVariable String userId) {
        return SecurityUtils.currentUserId()
                .flatMap(viewerId -> getOrCreateConversationUseCase.execute(viewerId, userId))
                .map(conversationId -> ResponseEntity.ok(ApiResponse.success(Map.of("id", conversationId), "Conversation ready")));
    }

    @Operation(summary = "Message history for a conversation, oldest first")
    @GetMapping("/{id}/messages")
    public Mono<ResponseEntity<ApiResponse<List<MessagingDtos.DirectMessageResponse>>>> messages(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(viewerId -> getMessagesUseCase.execute(id, viewerId, page, size))
                .map(MessagingDtos.DirectMessageResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Messages")));
    }

    @Operation(summary = "Send a message in a conversation")
    @PostMapping("/{id}/messages")
    public Mono<ResponseEntity<ApiResponse<MessagingDtos.DirectMessageResponse>>> send(
            @PathVariable String id,
            @RequestBody MessagingDtos.SendMessageRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMap(senderId -> sendMessageUseCase.execute(id, senderId, request.getContent()))
                .map(MessagingDtos.DirectMessageResponse::from)
                .map(message -> ResponseEntity.ok(ApiResponse.success(message, "Message sent")));
    }

    @Operation(summary = "Mark every message in a conversation from the other person as read")
    @PostMapping("/{id}/read")
    public Mono<ResponseEntity<ApiResponse<Void>>> markRead(@PathVariable String id) {
        return SecurityUtils.currentUserId()
                .flatMap(readerId -> markReadUseCase.execute(id, readerId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Marked read")));
    }

    @Operation(
            summary = "Live stream of incoming messages across all of the current user's conversations",
            description = "One SSE connection covers every conversation; each event's data is a DirectMessageResponse. " +
                          "Requires the same Bearer auth as any other endpoint (send it as a normal Authorization header, " +
                          "e.g. via fetch + ReadableStream — the browser's native EventSource can't set custom headers)."
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<MessagingDtos.DirectMessageResponse>> stream() {
        return SecurityUtils.currentUserId()
                .flatMapMany(messageBroadcaster::subscribe)
                .map(message -> ServerSentEvent.builder(MessagingDtos.DirectMessageResponse.from(message)).build());
    }
}
