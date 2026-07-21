package com.techpulse.techradar.features.system.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Shared serialize -> Redis Pub/Sub publish -> interpret-subscriber-count boilerplate for the
 * admin "trigger" endpoints ({@link AdminDataPlatformController}, {@link CrawlerAdminController})
 * that kick off out-of-process Python jobs which have no HTTP server of their own to call
 * directly: the payload is serialized to JSON, published on a Redis channel, and the response
 * reports whether any subscriber picked it up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisTriggerPublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param channel            Redis Pub/Sub channel to publish {@code payload} on
     * @param payload            serialized to JSON as the message body
     * @param deliveredMessage   human-readable message when at least one subscriber received it
     * @param notDeliveredMessage human-readable message when nothing was listening
     */
    Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> publish(
            String channel, Object payload, String deliveredMessage, String notDeliveredMessage) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize trigger event for channel {}", channel, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Map<String, Object>>error("Không thể gửi yêu cầu kích hoạt", "SERIALIZATION_ERROR")));
        }
        return redisTemplate.convertAndSend(channel, json)
                .map(subscribers -> {
                    boolean delivered = subscribers != null && subscribers > 0;
                    return ResponseEntity.ok(ApiResponse.success(
                            Map.<String, Object>of("delivered", delivered),
                            delivered ? deliveredMessage : notDeliveredMessage));
                });
    }
}
