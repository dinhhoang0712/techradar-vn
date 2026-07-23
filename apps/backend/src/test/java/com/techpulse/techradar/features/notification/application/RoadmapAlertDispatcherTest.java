package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapAlertDispatcherTest {

    @Mock
    private AlertDeliveryDispatcher alertDeliveryDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RoadmapAlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new RoadmapAlertDispatcher(alertDeliveryDispatcher, objectMapper);
    }

    private ConsumerRecord<String, String> record(RoadmapAlertEvent event) throws Exception {
        return new ConsumerRecord<>("roadmap.alerts", 0, 0L, "key", objectMapper.writeValueAsString(event));
    }

    @Test
    void onRoadmapAlert_dispatchesCareerAlert_withFormattedTitleAndBody() throws Exception {
        UUID userId = UUID.randomUUID();
        RoadmapAlertEvent event = new RoadmapAlertEvent(userId.toString(), "dev@example.com", true, false, "Rust", 25.0);
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any())).thenReturn(Mono.empty());

        dispatcher.onRoadmapAlert(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("CAREER_ALERT"), eq("Nên học tiếp: Rust"),
                eq("Rust đang tăng trưởng 25% và phù hợp với lộ trình của bạn. Xem chi tiết trên trang Sự nghiệp."),
                eq("/career"), eq(true), eq(false), eq("dev@example.com"));
    }

    @Test
    void onRoadmapAlert_passesThroughNotifyFlagsAndEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        RoadmapAlertEvent event = new RoadmapAlertEvent(userId.toString(), "other@example.com", false, true, "Zig", 10.0);
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any())).thenReturn(Mono.empty());

        dispatcher.onRoadmapAlert(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("CAREER_ALERT"), any(), any(), eq("/career"),
                eq(false), eq(true), eq("other@example.com"));
    }

    @Test
    void onRoadmapAlert_swallowsException_whenDispatchFails() throws Exception {
        RoadmapAlertEvent event = new RoadmapAlertEvent(UUID.randomUUID().toString(), "dev@example.com", true, false, "Go", 15.0);
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(Mono.error(new RuntimeException("redis down")));

        dispatcher.onRoadmapAlert(record(event));
    }

    @Test
    void onRoadmapAlert_neverDispatches_whenPayloadIsMalformedJson() {
        ConsumerRecord<String, String> malformed = new ConsumerRecord<>("roadmap.alerts", 0, 0L, "key", "not-json");

        dispatcher.onRoadmapAlert(malformed);

        verify(alertDeliveryDispatcher, never()).dispatch(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
