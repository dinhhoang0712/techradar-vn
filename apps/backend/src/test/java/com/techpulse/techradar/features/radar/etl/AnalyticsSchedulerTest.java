package com.techpulse.techradar.features.radar.etl;

import com.techpulse.techradar.features.radar.application.RadarCacheKeys;
import com.techpulse.techradar.features.radar.realtime.RadarBroadcaster;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsSchedulerTest {

    @Mock
    private RadarAnalyticsEtlService etlService;
    @Mock
    private ReactiveRedisCache redisCache;
    @Mock
    private RadarBroadcaster radarBroadcaster;

    private AnalyticsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnalyticsScheduler(etlService, redisCache, radarBroadcaster);
    }

    @Test
    void scheduledRebuild_evictsCacheThenBroadcastsLiveSnapshot_inOrder() {
        when(etlService.rebuild()).thenReturn(Mono.just(7L));
        when(redisCache.evictByPattern(RadarCacheKeys.EVICT_ALL_PATTERN)).thenReturn(Mono.empty());
        when(radarBroadcaster.publishLatestSnapshot()).thenReturn(Mono.empty());

        scheduler.scheduledRebuild();

        InOrder order = inOrder(redisCache, radarBroadcaster);
        order.verify(redisCache).evictByPattern(RadarCacheKeys.EVICT_ALL_PATTERN);
        order.verify(radarBroadcaster).publishLatestSnapshot();
    }

    @Test
    void scheduledRebuild_neverBroadcasts_whenEtlFails() {
        when(etlService.rebuild()).thenReturn(Mono.error(new RuntimeException("Neo4j unreachable")));

        scheduler.scheduledRebuild();

        verify(radarBroadcaster, never()).publishLatestSnapshot();
    }
}
