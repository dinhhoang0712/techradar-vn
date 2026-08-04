package com.techpulse.techradar.features.roadmap.application;

import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.roadmap.domain.LevelMoveResult;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulateLevelMoveUseCaseTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private GetJobMatchesUseCase getJobMatchesUseCase;

    @Mock
    private ReactiveRedisCache redisCache;

    private SimulateLevelMoveUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new SimulateLevelMoveUseCase(userProfileRepository, getJobMatchesUseCase, redisCache);
    }

    private static JobMatch matchWithSalary(double minVnd, double maxVnd) {
        return new JobMatch("Backend Dev", "Acme", "Hanoi", null, minVnd, maxVnd, "Senior", null, null,
                List.of(), List.of(), 1.0);
    }

    @Test
    void execute_computesCurrentAndSimulatedJobMatchCountsAtDifferentLevels() {
        UserProfile profile = UserProfile.builder().technologies(List.of("Docker")).currentLevel("Middle").build();
        when(userProfileRepository.findByUserId("user-1")).thenReturn(Mono.just(profile));
        when(getJobMatchesUseCase.executeForSkills(List.of("Docker"), "Middle", 100))
                .thenReturn(Flux.just(matchWithSalary(20, 30)));
        when(getJobMatchesUseCase.executeForSkills(List.of("Docker"), "Senior", 100))
                .thenReturn(Flux.just(matchWithSalary(30, 40), matchWithSalary(40, 50), matchWithSalary(50, 60)));

        StepVerifier.create(useCase.execute("user-1", "Senior"))
                .assertNext(result -> {
                    assertThat(result.currentLevel()).isEqualTo("Middle");
                    assertThat(result.targetLevel()).isEqualTo("Senior");
                    assertThat(result.currentJobMatches()).isEqualTo(1);
                    assertThat(result.simulatedJobMatches()).isEqualTo(3);
                    assertThat(result.salary()).isNotNull();
                    assertThat(result.salary().medianVnd()).isEqualTo(45.0);
                })
                .verifyComplete();
    }

    @Test
    void execute_usesUnfilteredMatchesAsCurrentBaseline_whenProfileHasNoCurrentLevel() {
        UserProfile profile = UserProfile.builder().technologies(List.of("Java")).currentLevel(null).build();
        when(userProfileRepository.findByUserId("user-1")).thenReturn(Mono.just(profile));
        when(getJobMatchesUseCase.executeForSkills(eq(List.of("Java")), isNull(), eq(100)))
                .thenReturn(Flux.just(matchWithSalary(10, 20), matchWithSalary(20, 30)));
        when(getJobMatchesUseCase.executeForSkills(List.of("Java"), "Senior", 100))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("user-1", "Senior"))
                .assertNext(result -> {
                    assertThat(result.currentLevel()).isNull();
                    assertThat(result.currentJobMatches()).isEqualTo(2);
                    assertThat(result.simulatedJobMatches()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void execute_defaultsToEmptyProfile_whenProfileDoesNotExist() {
        when(userProfileRepository.findByUserId("user-1")).thenReturn(Mono.empty());
        when(getJobMatchesUseCase.executeForSkills(eq(List.of()), isNull(), eq(100))).thenReturn(Flux.empty());
        when(getJobMatchesUseCase.executeForSkills(List.of(), "Senior", 100)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("user-1", "Senior"))
                .assertNext(result -> assertThat(result.currentJobMatches()).isZero())
                .verifyComplete();
    }

    @Test
    void execute_returnsNullSalaryWhenNoSimulatedMatchHasParsedSalary() {
        UserProfile profile = UserProfile.builder().technologies(List.of("Rust")).currentLevel("Junior").build();
        when(userProfileRepository.findByUserId("user-1")).thenReturn(Mono.just(profile));
        when(getJobMatchesUseCase.executeForSkills(List.of("Rust"), "Junior", 100)).thenReturn(Flux.empty());
        when(getJobMatchesUseCase.executeForSkills(List.of("Rust"), "Senior", 100))
                .thenReturn(Flux.just(new JobMatch("Rust Dev", "Acme", "Hanoi", null, null, null, "Senior", null,
                        null, List.of(), List.of(), 1.0)));

        StepVerifier.create(useCase.execute("user-1", "Senior"))
                .assertNext(result -> assertThat(result.salary().jobsWithSalary()).isZero())
                .verifyComplete();
    }

    @Test
    void execute_rejectsBlankTargetLevel() {
        StepVerifier.create(useCase.execute("user-1", "   "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void execute_rejectsUnknownLevel() {
        StepVerifier.create(useCase.execute("user-1", "Ninja"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
