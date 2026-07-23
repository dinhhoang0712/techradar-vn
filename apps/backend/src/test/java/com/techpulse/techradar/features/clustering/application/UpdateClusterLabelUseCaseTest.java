package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateClusterLabelUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;
    @Mock
    private ReactiveRedisCache redisCache;

    private UpdateClusterLabelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateClusterLabelUseCase(clusteringServicePort, redisCache);
    }

    @Test
    void execute_overridesLabelAndEvictsClustersCache() {
        when(clusteringServicePort.updateClusterLabel(eq("3"), any(), eq("admin-1")))
                .thenReturn(Mono.just(Map.of("cluster_id", 3, "label", "Backend", "overridden", true)));
        when(redisCache.evictByPattern("cache:clustering:clusters:*")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("3", "Backend", null, null, null, "admin-1"))
                .assertNext(result -> assertThat(result).containsEntry("overridden", true))
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(clusteringServicePort).updateClusterLabel(eq("3"), bodyCaptor.capture(), eq("admin-1"));
        assertThat(bodyCaptor.getValue()).containsExactly(Map.entry("label", "Backend"));
        verify(redisCache).evictByPattern("cache:clustering:clusters:*");
    }

    @Test
    void execute_forwardsAllProvidedFieldsInSnakeCase() {
        when(clusteringServicePort.updateClusterLabel(eq("3"), any(), isNull()))
                .thenReturn(Mono.just(Map.of("cluster_id", 3)));
        when(redisCache.evictByPattern(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("3", "Backend", "Backend EN", "Backend techs", "backend", null))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(clusteringServicePort).updateClusterLabel(eq("3"), bodyCaptor.capture(), isNull());
        assertThat(bodyCaptor.getValue())
                .containsEntry("label", "Backend")
                .containsEntry("label_en", "Backend EN")
                .containsEntry("description", "Backend techs")
                .containsEntry("domain", "backend");
    }

    @Test
    void execute_rejectsNullClusterId() {
        StepVerifier.create(useCase.execute(null, "Backend", null, null, null, "admin-1"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).updateClusterLabel(any(), any(), any());
        verify(redisCache, never()).evictByPattern(any());
    }

    @Test
    void execute_rejectsBlankClusterId() {
        StepVerifier.create(useCase.execute("   ", "Backend", null, null, null, "admin-1"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(clusteringServicePort, never()).updateClusterLabel(any(), any(), any());
        verify(redisCache, never()).evictByPattern(any());
    }

    @Test
    void execute_rejectsWhenAllOverridableFieldsAreNull() {
        StepVerifier.create(useCase.execute("3", null, null, null, null, "admin-1"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                    assertThat(ex).hasMessage("At least one of label/labelEn/description/domain is required");
                })
                .verify();

        verify(clusteringServicePort, never()).updateClusterLabel(any(), any(), any());
        verify(redisCache, never()).evictByPattern(any());
    }

    @Test
    void execute_doesNotEvictCache_whenPortCallFails() {
        RuntimeException boom = new RuntimeException("cluster 999 không tồn tại");
        when(clusteringServicePort.updateClusterLabel(eq("999"), any(), any())).thenReturn(Mono.error(boom));

        StepVerifier.create(useCase.execute("999", "X", null, null, null, "admin-1"))
                .expectErrorMatches(ex -> ex == boom)
                .verify();

        verify(redisCache, never()).evictByPattern(any());
    }
}
