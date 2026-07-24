package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.application.RadarCacheKeys;
import com.techpulse.techradar.features.radar.etl.RadarAnalyticsEtlService;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.radar.realtime.RadarBroadcaster;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsAdminControllerTest {

    @Mock
    private RadarAnalyticsEtlService etlService;
    @Mock
    private ReactiveRedisCache redisCache;
    @Mock
    private RadarBroadcaster radarBroadcaster;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;

    private AnalyticsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsAdminController(etlService, redisCache, radarBroadcaster, auditLogService, notificationService);
        lenient().when(auditLogService.record(any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(notificationService.notifyAllAdmins(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void rebuild_evictsCacheThenBroadcastsLiveSnapshot_inOrder() {
        when(etlService.rebuild()).thenReturn(Mono.just(42L));
        when(redisCache.evictByPattern(RadarCacheKeys.EVICT_ALL_PATTERN)).thenReturn(Mono.empty());
        when(radarBroadcaster.publishLatestSnapshot()).thenReturn(Mono.empty());

        StepVerifier.create(controller.rebuild())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).containsEntry("rows_upserted", 42L);
                })
                .verifyComplete();

        InOrder order = inOrder(redisCache, radarBroadcaster);
        order.verify(redisCache).evictByPattern(RadarCacheKeys.EVICT_ALL_PATTERN);
        order.verify(radarBroadcaster).publishLatestSnapshot();

        verify(auditLogService).record(eq("ANALYTICS_REBUILD"), eq("analytics"), any(), any());
        verify(notificationService).notifyAllAdmins(eq("ADMIN_ANALYTICS_REBUILD_DONE"), any(), any(), eq("/admin/automation"));
    }

    @Test
    void rebuild_surfacesEtlFailure_as503_andNeverBroadcasts() {
        when(etlService.rebuild()).thenReturn(Mono.error(new RuntimeException("Neo4j unreachable")));

        StepVerifier.create(controller.rebuild())
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(503))
                .verifyComplete();

        verify(notificationService).notifyAllAdmins(eq("ADMIN_ANALYTICS_REBUILD_FAILED"), any(), any(), eq("/admin/automation"));
    }
}
