package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendAlertDispatcherTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private AlertDeliveryDispatcher alertDeliveryDispatcher;

    @Mock
    private AiProxyPort aiProxyPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TrendAlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TrendAlertDispatcher(repository, alertDeliveryDispatcher, objectMapper, aiProxyPort);
        ReflectionTestUtils.setField(dispatcher, "summarizeTimeoutMs", 30000L);
    }

    private ConsumerRecord<String, String> record(TrendAlertEvent event) throws Exception {
        return new ConsumerRecord<>("trend.alerts", 0, 0L, "key", objectMapper.writeValueAsString(event));
    }

    @Test
    void onTrendAlert_appendsNarrativeWhenSummarizeReturnsGroundedSources() throws Exception {
        TrendAlertEvent event = new TrendAlertEvent("Rust", 340.0, 340.0, 12, "2026-05");
        UUID userId = UUID.randomUUID();
        when(repository.findTrendSubscribers("Rust")).thenReturn(Flux.just(
                new TrendSubscriber(userId, "dev@example.com", true, false)));
        when(aiProxyPort.forward(eq("/summarize"),
                eq(Map.of("tech_name", "Rust", "period", "2026-05", "format", "paragraph")), any()))
                .thenReturn(Mono.just(Map.of(
                        "summary", "Rust tăng mạnh nhờ loạt công ty fintech tuyển dụng vị trí backend.",
                        "sources_used", 5)));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onTrendAlert(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("TREND_ALERT"), eq("Xu hướng tăng: Rust"),
                eq("Rust đang tăng 340% so với tháng trước (nhu cầu tuyển dụng hiện tại: 12 vị trí). " +
                        "Xem chi tiết trên trang Radar. Rust tăng mạnh nhờ loạt công ty fintech tuyển dụng vị trí backend."),
                eq("/radar"), eq(true), eq(false), eq("dev@example.com"));
    }

    @Test
    void onTrendAlert_fallsBackToBaseBodyWhenSourcesUsedIsZero() throws Exception {
        TrendAlertEvent event = new TrendAlertEvent("COBOL", 40.0, 40.0, 2, "2026-05");
        UUID userId = UUID.randomUUID();
        when(repository.findTrendSubscribers("COBOL")).thenReturn(Flux.just(
                new TrendSubscriber(userId, "dev@example.com", true, false)));
        when(aiProxyPort.forward(eq("/summarize"), any(), any())).thenReturn(Mono.just(Map.of(
                "summary", "Không tìm thấy bài viết nào.", "sources_used", 0)));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onTrendAlert(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("TREND_ALERT"), any(),
                eq("COBOL đang tăng 40% so với tháng trước (nhu cầu tuyển dụng hiện tại: 2 vị trí). " +
                        "Xem chi tiết trên trang Radar."),
                eq("/radar"), eq(true), eq(false), eq("dev@example.com"));
    }

    @Test
    void onTrendAlert_swallowsSummarizeFailureAndStillDispatchesBaseBody() throws Exception {
        TrendAlertEvent event = new TrendAlertEvent("Zig", 100.0, 100.0, 3, "2026-05");
        UUID userId = UUID.randomUUID();
        when(repository.findTrendSubscribers("Zig")).thenReturn(Flux.just(
                new TrendSubscriber(userId, "dev@example.com", true, false)));
        when(aiProxyPort.forward(eq("/summarize"), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("ai-rag-core timeout")));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onTrendAlert(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("TREND_ALERT"), any(),
                eq("Zig đang tăng 100% so với tháng trước (nhu cầu tuyển dụng hiện tại: 3 vị trí). " +
                        "Xem chi tiết trên trang Radar."),
                eq("/radar"), eq(true), eq(false), eq("dev@example.com"));
    }

    @Test
    void onTrendAlert_callsSummarizeExactlyOnceRegardlessOfSubscriberCount() throws Exception {
        TrendAlertEvent event = new TrendAlertEvent("Go", 50.0, 50.0, 8, "2026-05");
        when(repository.findTrendSubscribers("Go")).thenReturn(Flux.just(
                new TrendSubscriber(UUID.randomUUID(), "a@example.com", true, false),
                new TrendSubscriber(UUID.randomUUID(), "b@example.com", true, false),
                new TrendSubscriber(UUID.randomUUID(), "c@example.com", true, false)));
        when(aiProxyPort.forward(eq("/summarize"), any(), any())).thenReturn(Mono.just(Map.of(
                "summary", "Go tăng nhờ nhu cầu microservices.", "sources_used", 3)));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onTrendAlert(record(event));

        verify(aiProxyPort, times(1)).forward(eq("/summarize"), any(), any());
        verify(alertDeliveryDispatcher, times(3))
                .dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }
}
