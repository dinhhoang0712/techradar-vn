package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClustersUseCaseTest {

    @Mock
    private ClusteringServicePort clusteringServicePort;
    @Mock
    private ReactiveRedisCache redisCache;

    private GetClustersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClustersUseCase(clusteringServicePort, redisCache);
        ReflectionTestUtils.setField(useCase, "cacheTtlSeconds", 900L);
    }

    private void stubCacheAsPassThrough() {
        when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void execute_delegatesToPortAndCachesUnderIsCoherentSpecificKey() {
        stubCacheAsPassThrough();
        when(clusteringServicePort.getClusters(true)).thenReturn(Flux.just(Map.of("cluster_id", 0)));

        StepVerifier.create(useCase.execute(true))
                .expectNext(Map.of("cluster_id", 0))
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:clustering:clusters:true");
    }

    @Test
    void execute_usesNullSpecificCacheKey_whenIsCoherentNotProvided() {
        stubCacheAsPassThrough();
        when(clusteringServicePort.getClusters(null)).thenReturn(Flux.just(Map.of("cluster_id", 1)));

        StepVerifier.create(useCase.execute(null))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:clustering:clusters:null");
    }

    @Test
    void execute_appliesConfiguredTtlToCacheLookup() {
        ReflectionTestUtils.setField(useCase, "cacheTtlSeconds", 60L);
        stubCacheAsPassThrough();
        when(clusteringServicePort.getClusters(false)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(false)).verifyComplete();

        verify(redisCache).getOrLoad(anyString(), eq(Duration.ofSeconds(60)), any(Flux.class), any());
    }

    @Test
    void execute_propagatesPortErrorThroughCacheLoader() {
        RuntimeException boom = new RuntimeException("ml-clustering unavailable");
        stubCacheAsPassThrough();
        when(clusteringServicePort.getClusters(null)).thenReturn(Flux.error(boom));

        StepVerifier.create(useCase.execute(null))
                .expectErrorMatches(ex -> ex == boom)
                .verify();
    }
}
