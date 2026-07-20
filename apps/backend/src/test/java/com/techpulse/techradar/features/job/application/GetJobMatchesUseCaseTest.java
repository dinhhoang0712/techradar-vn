package com.techpulse.techradar.features.job.application;

import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetJobMatchesUseCaseTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetJobMatchesUseCase useCase;

    @BeforeEach
    void setUp() {
        // lenient: the no-skills test short-circuits before rawMatches() ever calls the cache.
        lenient().when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new GetJobMatchesUseCase(jobRepository, userProfileRepository, redisCache);
    }

    private static JobRepository.JobMatchRaw raw(String title, String location, List<String> required,
                                                  List<String> matched, double score) {
        return new JobRepository.JobMatchRaw(title, "Some Co", location, null, null, null, required, matched, score);
    }

    private void profileWith(List<String> technologies) {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(technologies).build()));
        when(jobRepository.findMatchingJobs(anyList(), anyInt())).thenReturn(Flux.empty());
    }

    @Test
    void cacheKey_isStableRegardlessOfSkillOrderCaseOrDuplicates() {
        when(jobRepository.findMatchingJobs(anyList(), anyInt())).thenReturn(Flux.empty());

        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Java", "java", "React")).build()));
        useCase.execute("user-1", null, null, 10).blockLast();

        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("react", "JAVA")).build()));
        useCase.execute("user-1", null, null, 10).blockLast();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache, times(2)).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        List<String> keys = keyCaptor.getAllValues();
        assertThat(keys.get(0)).isEqualTo(keys.get(1));
        assertThat(keys.get(0)).isEqualTo("cache:job:match:java|react");
    }

    @Test
    void rawFetch_isFixedAtMaxLimitTimesMultiplier_regardlessOfRequestedLimit() {
        profileWith(List.of("Java"));

        useCase.execute("user-1", null, null, 5).blockLast();

        verify(jobRepository).findMatchingJobs(eq(List.of("java")), eq(300));
    }

    @Test
    void execute_filtersByLocationAndSortsByScoreDescending() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Java", "React")).build()));
        when(jobRepository.findMatchingJobs(anyList(), anyInt())).thenReturn(Flux.just(
                raw("Backend Dev", "Hà Nội", List.of("Java", "Spring"), List.of("Java"), 5.0),
                raw("Frontend Dev", "Hồ Chí Minh", List.of("React"), List.of("React"), 9.0),
                raw("Fullstack Dev", "Hà Nội", List.of("Java", "React"), List.of("java", "REACT"), 7.0)
        ));

        StepVerifier.create(useCase.execute("user-1", "hà nội", null, 10).map(JobMatch::title))
                .expectNext("Fullstack Dev", "Backend Dev")
                .verifyComplete();
    }

    @Test
    void execute_dedupesMatchedAndMissingCaseInsensitively() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Java")).build()));
        when(jobRepository.findMatchingJobs(anyList(), anyInt())).thenReturn(Flux.just(
                raw("Backend Dev", "Hà Nội", List.of("Java", "Spring"), List.of("java"), 5.0)
        ));

        StepVerifier.create(useCase.execute("user-1", null, null, 10))
                .assertNext(match -> {
                    assertThat(match.matchedSkills()).containsExactly("java");
                    assertThat(match.missingSkills()).containsExactly("Spring");
                })
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyWhenUserHasNoSkills() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of()).build()));

        StepVerifier.create(useCase.execute("user-1", null, null, 10))
                .verifyComplete();
    }

    @Test
    void executeForSkills_bypassesProfileLookupAndScoresTheGivenListDirectly() {
        when(jobRepository.findMatchingJobs(anyList(), anyInt())).thenReturn(Flux.just(
                raw("Backend Dev", "Hà Nội", List.of("Java", "Kubernetes"), List.of("Java"), 5.0)
        ));

        StepVerifier.create(useCase.executeForSkills(List.of("Java"), 10).map(JobMatch::title))
                .expectNext("Backend Dev")
                .verifyComplete();

        verify(userProfileRepository, never()).findByUserId(anyString());
        verify(jobRepository).findMatchingJobs(eq(List.of("java")), anyInt());
    }
}
