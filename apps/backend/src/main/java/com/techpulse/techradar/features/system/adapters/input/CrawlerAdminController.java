package com.techpulse.techradar.features.system.adapters.input;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.RedisLock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Admin-triggered on-demand crawl run ("Kích hoạt Radar"). The crawler is a standalone Python
 * container (services/crawler, docker-compose profile {@code crawl}) with no HTTP server of its
 * own; it wakes early from its sleep loop on a Redis Pub/Sub signal, the same cross-process
 * mechanism {@code MessageBroadcaster} uses for SSE fan-out. This controller only publishes
 * {@code crawler:trigger} and reads the {@code crawler:status} key the crawler writes — it never
 * subscribes to anything.
 */
@Slf4j
@Tag(name = "Admin", description = "Crawler on-demand trigger")
@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerAdminController {

    private static final String TRIGGER_CHANNEL = "crawler:trigger";
    private static final String STATUS_KEY = "crawler:status";
    private static final String LOCK_KEY = "crawler:trigger:lock";
    private static final TypeReference<Map<String, Object>> STATUS_TYPE = new TypeReference<>() {
    };

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTriggerPublisher redisTriggerPublisher;

    @Operation(summary = "Trigger an immediate crawl run instead of waiting for the crawler's own schedule")
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('crawler:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> trigger() {
        return RedisLock.tryAcquire(redisTemplate, LOCK_KEY, Duration.ofSeconds(10))
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                                ApiResponse.<Map<String, Object>>error(
                                        "Vừa mới kích hoạt, vui lòng đợi vài giây rồi thử lại", "CRAWL_DEBOUNCED")));
                    }
                    return readStatus().flatMap(status -> {
                        if ("running".equals(status.get("state"))) {
                            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(
                                    ApiResponse.<Map<String, Object>>error(
                                            "Đang có một lượt cào dữ liệu chạy, vui lòng đợi", "CRAWL_IN_PROGRESS")));
                        }
                        return publishTrigger();
                    });
                });
    }

    @Operation(summary = "Get the last known crawl run status")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('crawler:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> status() {
        return readStatus().map(status -> ResponseEntity.ok(ApiResponse.success(status)));
    }

    private Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> publishTrigger() {
        // The crawler only runs under --profile crawl: unlike MessageBroadcaster's SSE fan-out
        // (where "zero subscribers" is a transient rolling-deploy blip), the container simply
        // not being up is a plausible persistent state here, so surface it instead of hiding it.
        return redisTriggerPublisher.publish(
                TRIGGER_CHANNEL,
                Map.of("triggeredAt", Instant.now().toString()),
                "Đã kích hoạt Radar, crawler sẽ chạy ngay",
                "Đã gửi yêu cầu nhưng không có crawler nào đang lắng nghe "
                        + "(kiểm tra container crawler đã bật --profile crawl chưa)");
    }

    private Mono<Map<String, Object>> readStatus() {
        return redisTemplate.opsForValue().get(STATUS_KEY)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, STATUS_TYPE));
                    } catch (Exception e) {
                        log.warn("Could not parse crawler status from Redis", e);
                        return Mono.<Map<String, Object>>just(Map.of("state", "unknown"));
                    }
                })
                .defaultIfEmpty(Map.<String, Object>of("state", "idle"));
    }
}
