package com.techpulse.techradar.features.roadmap.application;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.graph.application.RoadAnalysisUseCase;
import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.roadmap.domain.RoadmapResult;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCareerRoadmapUseCaseTest {

    @Mock
    private AiProxyPort aiProxyPort;

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private UserProfileRepository userProfileRepository;

    @Mock
    private GetJobMatchesUseCase getJobMatchesUseCase;

    @Mock
    private RoadAnalysisUseCase roadAnalysisUseCase;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetCareerRoadmapUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new GetCareerRoadmapUseCase(
                aiProxyPort, userProfileRepository, getJobMatchesUseCase, roadAnalysisUseCase, redisCache);
    }

    @Test
    void execute_returnsEmptyFlagAndSkipsAiCallsWhenProfileHasNoTechnologies() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of()).build()));

        StepVerifier.create(useCase.execute("user-1"))
                .assertNext(result -> {
                    assertThat(result.hasTechnologies()).isFalse();
                    assertThat(result.nextSkills()).isEmpty();
                    assertThat(result.jobMatches()).isEmpty();
                })
                .verifyComplete();

        verify(aiProxyPort, never()).forward(anyString(), any(), any());
        verify(getJobMatchesUseCase, never()).execute(anyString(), any(), any(), anyInt());
        verify(roadAnalysisUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void execute_aggregatesRecommendCareerAndJobMatchesAndCrossReferencesMissingSkills() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));

        Map<String, Object> recommendResponse = Map.of(
                "recommendations", List.of(
                        Map.<String, Object>of("tech_name", "Kubernetes", "reason", "...", "growth_rate", 42.0),
                        Map.<String, Object>of("tech_name", "Helm", "reason", "...", "growth_rate", 5.0)
                ),
                "based_on", List.of("Docker"));
        Map<String, Object> careerResponse = Map.of(
                "target_role", "Senior Backend Developer",
                "skill_gap", List.of(),
                "roadmap", "...",
                "estimated_months", 6);

        when(aiProxyPort.forward(eq("/recommend"), any(), any())).thenReturn(Mono.just(recommendResponse));
        when(aiProxyPort.forward(eq("/career"), any(), any())).thenReturn(Mono.just(careerResponse));

        JobMatch jobMatch = new JobMatch("Backend Dev", "Acme", "Hanoi", null, null, null, null, null,
                List.of("Docker"), List.of("Kubernetes"), 0.5);
        when(getJobMatchesUseCase.execute(eq("user-1"), isNull(), isNull(), anyInt()))
                .thenReturn(Flux.just(jobMatch));

        when(roadAnalysisUseCase.execute("Docker", "Kubernetes")).thenReturn(Mono.just(GraphData.builder()
                .nodes(List.of(
                        GraphNode.builder().id("1").name("Docker").build(),
                        GraphNode.builder().id("2").name("Kubernetes").build()))
                .edges(List.of())
                .found(true)
                .build()));
        when(roadAnalysisUseCase.execute("Docker", "Helm")).thenReturn(Mono.just(GraphData.builder()
                .nodes(List.of())
                .edges(List.of())
                .found(false)
                .build()));

        StepVerifier.create(useCase.execute("user-1"))
                .assertNext(result -> {
                    assertThat(result.hasTechnologies()).isTrue();
                    assertThat(result.currentTechnologies()).containsExactly("Docker");
                    assertThat(result.careerPath()).isEqualTo(careerResponse);
                    assertThat(result.jobMatches()).containsExactly(jobMatch);

                    assertThat(result.nextSkills()).hasSize(2);
                    Map<String, Object> kubernetes = result.nextSkills().get(0);
                    assertThat(kubernetes.get("tech_name")).isEqualTo("Kubernetes");
                    assertThat(kubernetes.get("job_matches_needing_it")).isEqualTo(1L);
                    assertThat(kubernetes.get("tech_path")).isEqualTo(List.of("Docker", "Kubernetes"));

                    Map<String, Object> helm = result.nextSkills().get(1);
                    assertThat(helm.get("job_matches_needing_it")).isEqualTo(0L);
                    assertThat(helm).doesNotContainKey("tech_path");
                })
                .verifyComplete();
    }

    @Test
    void execute_skipsPathLookupWhenRecommendationIsTheSameAsSourceTech() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));
        when(aiProxyPort.forward(eq("/recommend"), any(), any())).thenReturn(Mono.just(Map.of(
                "recommendations", List.of(Map.<String, Object>of("tech_name", "Docker", "growth_rate", 10.0)))));
        when(aiProxyPort.forward(eq("/career"), any(), any())).thenReturn(Mono.just(Map.of()));
        when(getJobMatchesUseCase.execute(anyString(), any(), any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("user-1"))
                .assertNext(result -> assertThat(result.nextSkills().get(0)).doesNotContainKey("tech_path"))
                .verifyComplete();

        verify(roadAnalysisUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void execute_ignoresRoadAnalysisFailureAndStillReturnsTheSkill() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));
        when(aiProxyPort.forward(eq("/recommend"), any(), any())).thenReturn(Mono.just(Map.of(
                "recommendations", List.of(Map.<String, Object>of("tech_name", "Kubernetes", "growth_rate", 10.0)))));
        when(aiProxyPort.forward(eq("/career"), any(), any())).thenReturn(Mono.just(Map.of()));
        when(getJobMatchesUseCase.execute(anyString(), any(), any(), anyInt())).thenReturn(Flux.empty());
        when(roadAnalysisUseCase.execute("Docker", "Kubernetes"))
                .thenReturn(Mono.error(new RuntimeException("neo4j unavailable")));

        StepVerifier.create(useCase.execute("user-1"))
                .assertNext(result -> {
                    assertThat(result.nextSkills()).hasSize(1);
                    assertThat(result.nextSkills().get(0)).doesNotContainKey("tech_path");
                })
                .verifyComplete();
    }

    @Test
    void execute_usesRoadmapCacheKeyedByUserId() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));
        when(aiProxyPort.forward(anyString(), any(), any())).thenReturn(Mono.just(Map.of()));
        when(getJobMatchesUseCase.execute(anyString(), any(), any(), anyInt())).thenReturn(Flux.empty());

        useCase.execute("user-1").block();

        verify(redisCache).getOrLoadMono(eq("cache:roadmap:user-1"), any(Duration.class), any(Mono.class), any());
    }

    @Test
    void execute_stillReturnsResultWhenRecommendOrCareerCallFails() {
        when(userProfileRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(UserProfile.builder().technologies(List.of("Docker")).build()));
        when(aiProxyPort.forward(eq("/recommend"), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("ai-rag-core unavailable")));
        when(aiProxyPort.forward(eq("/career"), any(), any())).thenReturn(Mono.just(Map.of("target_role", "X")));
        when(getJobMatchesUseCase.execute(anyString(), any(), any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("user-1"))
                .assertNext(result -> {
                    assertThat(result.hasTechnologies()).isTrue();
                    assertThat(result.nextSkills()).isEmpty();
                    assertThat(result.careerPath()).containsEntry("target_role", "X");
                })
                .verifyComplete();
    }
}
