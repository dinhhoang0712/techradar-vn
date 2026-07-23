package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheAdminControllerTest {

    @Mock
    private ReactiveRedisCache redisCache;

    private CacheAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CacheAdminController(redisCache);
    }

    @Test
    void evictCompanies_evictsExactlyTheCompanyAllKey() {
        when(redisCache.evict("cache:company:all")).thenReturn(Mono.empty());

        StepVerifier.create(controller.evictCompanies())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                })
                .verifyComplete();

        verify(redisCache).evict("cache:company:all");
    }

    @Test
    void evictJobs_evictsTheJobMatchPattern() {
        when(redisCache.evictByPattern("cache:job:match:*")).thenReturn(Mono.empty());

        StepVerifier.create(controller.evictJobs())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                })
                .verifyComplete();

        verify(redisCache).evictByPattern("cache:job:match:*");
    }

    @Test
    void evictRoadmaps_evictsTheRoadmapPattern() {
        when(redisCache.evictByPattern("cache:roadmap:*")).thenReturn(Mono.empty());

        StepVerifier.create(controller.evictRoadmaps())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                })
                .verifyComplete();

        verify(redisCache).evictByPattern("cache:roadmap:*");
    }
}
