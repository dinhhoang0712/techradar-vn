package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.system.domain.PublicStats;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPublicStatsUseCaseTest {

    @Mock
    private GetCompaniesUseCase getCompaniesUseCase;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetPublicStatsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPublicStatsUseCase(getCompaniesUseCase, jobRepository, userRepository, redisCache);
    }

    private void stubCacheAsPassThrough() {
        when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    private static CompanyProfile profile(String id) {
        return new CompanyProfile(id, "Company " + id, "Hà Nội", List.of("Java"), 1, null, null);
    }

    @Test
    void execute_zipsAllThreeCounts_underThePublicStatsCacheKey() {
        stubCacheAsPassThrough();
        when(getCompaniesUseCase.all()).thenReturn(Flux.fromIterable(
                List.of(profile("1"), profile("2"), profile("3"))));
        when(jobRepository.countJobs()).thenReturn(Mono.just(42L));
        when(userRepository.countAll()).thenReturn(Mono.just(7L));

        StepVerifier.create(useCase.execute())
                .assertNext(stats -> {
                    assertThat(stats.companies()).isEqualTo(3L);
                    assertThat(stats.jobs()).isEqualTo(42L);
                    assertThat(stats.users()).isEqualTo(7L);
                })
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoadMono(keyCaptor.capture(), any(Duration.class), any(Mono.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:public-stats");
    }

    @Test
    void execute_countsCompaniesFromTheAlreadyCachedList_noSeparateCountQuery() {
        stubCacheAsPassThrough();
        when(getCompaniesUseCase.all()).thenReturn(Flux.fromIterable(
                List.of(profile("1"), profile("2"))));
        when(jobRepository.countJobs()).thenReturn(Mono.just(0L));
        when(userRepository.countAll()).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute())
                .assertNext(stats -> assertThat(stats.companies()).isEqualTo(2L))
                .verifyComplete();

        // GetCompaniesUseCase.all() is itself Redis-cached — GetPublicStatsUseCase must reuse it
        // rather than issuing a second, separate count query.
        verify(getCompaniesUseCase).all();
    }

    @Test
    void execute_onCacheHit_neverRecomputesFromRealSources() {
        PublicStats cached = new PublicStats(500, 1000, 250);
        when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenReturn(Mono.just(cached));

        StepVerifier.create(useCase.execute())
                .expectNext(cached)
                .verifyComplete();

        verify(getCompaniesUseCase, never()).all();
        verify(jobRepository, never()).countJobs();
        verify(userRepository, never()).countAll();
    }
}
