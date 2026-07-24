package com.techpulse.techradar.features.system.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerAdminControllerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CrawlerAdminController controller;

    @BeforeEach
    void setUp() {
        RedisTriggerPublisher redisTriggerPublisher = new RedisTriggerPublisher(redisTemplate, objectMapper);
        controller = new CrawlerAdminController(redisTemplate, objectMapper, redisTriggerPublisher, auditLogService);
        lenient().when(auditLogService.record(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void trigger_publishesAndReportsDelivered_whenIdleAndSubscriberPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("crawler:trigger:lock"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(true));
        when(valueOperations.get("crawler:status")).thenReturn(Mono.empty());
        when(redisTemplate.convertAndSend(eq("crawler:trigger"), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(controller.trigger())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsEntry("delivered", true);
                })
                .verifyComplete();

        verify(auditLogService).record(eq("CRAWLER_TRIGGER"), eq("crawler"), any(), any());
    }

    @Test
    void trigger_publishesButReportsNotDelivered_whenNoSubscriberIsListening() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("crawler:trigger:lock"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(true));
        when(valueOperations.get("crawler:status")).thenReturn(Mono.empty());
        when(redisTemplate.convertAndSend(eq("crawler:trigger"), anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(controller.trigger())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).containsEntry("delivered", false);
                })
                .verifyComplete();
    }

    @Test
    void trigger_returnsConflict_whenACrawlIsAlreadyRunning() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("crawler:trigger:lock"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(true));
        when(valueOperations.get("crawler:status"))
                .thenReturn(Mono.just("{\"state\":\"running\",\"started_at\":\"2026-07-17T00:00:00\"}"));

        StepVerifier.create(controller.trigger())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("CRAWL_IN_PROGRESS");
                })
                .verifyComplete();

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(auditLogService, never()).record(any(), any(), any(), any());
    }

    @Test
    void trigger_returnsTooManyRequests_whenDebounceLockIsAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("crawler:trigger:lock"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(false));

        StepVerifier.create(controller.trigger())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("CRAWL_DEBOUNCED");
                })
                .verifyComplete();

        verify(valueOperations, never()).get("crawler:status");
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void status_returnsIdle_whenStatusKeyIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("crawler:status")).thenReturn(Mono.empty());

        StepVerifier.create(controller.status())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(response.getBody().getData()).containsEntry("state", "idle");
                })
                .verifyComplete();
    }

    @Test
    void status_returnsParsedPayload_whenStatusKeyIsPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("crawler:status")).thenReturn(Mono.just(
                "{\"state\":\"idle\",\"started_at\":\"2026-07-17T00:00:00\","
                        + "\"finished_at\":\"2026-07-17T00:05:00\",\"success_count\":8,\"total\":8}"));

        StepVerifier.create(controller.status())
                .assertNext(response -> {
                    Map<String, Object> data = response.getBody().getData();
                    assertThat(data).containsEntry("state", "idle");
                    assertThat(data).containsEntry("success_count", 8);
                    assertThat(data).containsEntry("total", 8);
                })
                .verifyComplete();
    }

    @Test
    void status_fallsBackToUnknown_whenStoredJsonIsMalformed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("crawler:status")).thenReturn(Mono.just("not-json"));

        StepVerifier.create(controller.status())
                .assertNext(response -> assertThat(response.getBody().getData()).containsEntry("state", "unknown"))
                .verifyComplete();
    }
}
