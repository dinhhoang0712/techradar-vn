package com.techpulse.techradar.features.chat.ports;

import com.techpulse.techradar.features.chat.domain.ChatMessage;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatSessionItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatRepository {

    Mono<ChatSession> saveSession(ChatSession session);

    Mono<ChatSession> findSessionById(String sessionId);

    Mono<ChatMessage> saveMessage(ChatMessage message);

    Flux<ChatMessageItem> listMessages(String sessionId);

    Flux<ChatSessionItem> listSessionsByUser(String userId);

    /**
     * @return the number of session rows deleted (0 when not found). Messages cascade via FK.
     */
    Mono<Long> deleteSession(String sessionId);
}
