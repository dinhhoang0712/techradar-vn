package com.techpulse.techradar.features.roadmap.application;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.roadmap.domain.SimulationResult;
import com.techpulse.techradar.features.salary.application.GetTechSalaryDetailUseCase;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulateCareerMoveUseCaseTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private UserProfileRepository userProfileRepository;

    @Mock
    private GetJobMatchesUseCase getJobMatchesUseCase;

    @Mock
    private GetTechSalaryDetailUseCase getTechSalaryDetailUseCase;

    @Mock
    private AiProxyPort aiProxyPort;

    @Mock
    private ReactiveRedisCache redisCache;

    private SimulateCareerMoveUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new SimulateCareerMoveUseCase(
                userProfileRepository, getJobMatchesUseCase, getTechSalaryDetailUseCase, aiProxyPort, redisCache);
    }

    private static JobMatch match(String title) {
        return new JobMatch(title, "Acme", "Hanoi", null, null, null, null, null, List.of(), List.of(), 1.0);
    }

    @Test
    void execute_addsHypotheticalTechToCurrentSkillsForTheSimulatedCall() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));
        when(getJobMatchesUseCase.executeForSkills(eq(List.of("Docker")), anyInt()))
                .thenReturn(Flux.just(match("A")));
        when(getJobMatchesUseCase.executeForSkills(eq(List.of("Docker", "Kubernetes")), anyInt()))
                .thenReturn(Flux.just(match("A"), match("B"), match("C")));
        when(aiProxyPort.forward(eq("/forecast"), any(), any())).thenReturn(Mono.just(Map.of(
                "predicted_direction", "growing", "confidence", 0.8)));
        when(getTechSalaryDetailUseCase.execute("Kubernetes")).thenReturn(Mono.just(
                new SalaryInsight("Kubernetes", 40, 30, 32.0, 33.5, 18.0, 60.0, 25.0, 40.0, List.of("Docker"))));

        StepVerifier.create(useCase.execute("user-1", "Kubernetes"))
                .assertNext(result -> {
                    assertThat(result.technology()).isEqualTo("Kubernetes");
                    assertThat(result.currentJobMatches()).isEqualTo(1L);
                    assertThat(result.simulatedJobMatches()).isEqualTo(3L);
                    assertThat(result.salary().medianVnd()).isEqualTo(32.0);
                    assertThat(result.forecast()).containsEntry("predicted_direction", "growing");
                })
                .verifyComplete();
    }

    @Test
    void execute_doesNotDuplicateTechAlreadyInCurrentSkills() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Kubernetes")).build()));
        when(getJobMatchesUseCase.executeForSkills(eq(List.of("Kubernetes")), anyInt()))
                .thenReturn(Flux.just(match("A")));
        when(aiProxyPort.forward(eq("/forecast"), any(), any())).thenReturn(Mono.just(Map.of()));
        when(getTechSalaryDetailUseCase.execute("Kubernetes"))
                .thenReturn(Mono.error(new NotFoundException("Technology not found: Kubernetes")));

        StepVerifier.create(useCase.execute("user-1", "Kubernetes"))
                .assertNext(result -> {
                    assertThat(result.currentJobMatches()).isEqualTo(1L);
                    assertThat(result.simulatedJobMatches()).isEqualTo(1L);
                })
                .verifyComplete();

        ArgumentCaptor<List> skillsCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(getJobMatchesUseCase, org.mockito.Mockito.times(2))
                .executeForSkills(skillsCaptor.capture(), anyInt());
        assertThat(skillsCaptor.getAllValues()).allMatch(list -> list.equals(List.of("Kubernetes")));
    }

    @Test
    void execute_returnsNullSalaryWhenTechHasNoSalaryData() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of()).build()));
        when(getJobMatchesUseCase.executeForSkills(any(), anyInt())).thenReturn(Flux.empty());
        when(aiProxyPort.forward(eq("/forecast"), any(), any())).thenReturn(Mono.just(Map.of()));
        when(getTechSalaryDetailUseCase.execute("Rust"))
                .thenReturn(Mono.error(new NotFoundException("Technology not found: Rust")));

        StepVerifier.create(useCase.execute("user-1", "Rust"))
                .assertNext(result -> assertThat(result.salary()).isNull())
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyForecastMapWhenForecastCallFails() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of()).build()));
        when(getJobMatchesUseCase.executeForSkills(any(), anyInt())).thenReturn(Flux.empty());
        when(aiProxyPort.forward(eq("/forecast"), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("ai-rag-core unavailable")));
        when(getTechSalaryDetailUseCase.execute("Rust"))
                .thenReturn(Mono.error(new NotFoundException("Technology not found: Rust")));

        StepVerifier.create(useCase.execute("user-1", "Rust"))
                .assertNext(result -> assertThat(result.forecast()).isEmpty())
                .verifyComplete();
    }

    @Test
    void execute_rejectsBlankTechnology() {
        StepVerifier.create(useCase.execute("user-1", "   "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
