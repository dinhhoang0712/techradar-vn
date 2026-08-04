package com.techpulse.techradar.features.roadmap.adapters.input;

import com.techpulse.techradar.features.roadmap.application.GetCareerRoadmapUseCase;
import com.techpulse.techradar.features.roadmap.application.SimulateCareerMoveUseCase;
import com.techpulse.techradar.features.roadmap.application.SimulateLevelMoveUseCase;
import com.techpulse.techradar.features.roadmap.domain.LevelMoveResult;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers only {@code /career/simulate-level} — {@code /career/roadmap} and {@code
 * /career/simulate} have no controller-level test yet (pre-existing gap, out of scope here).
 */
@ExtendWith(MockitoExtension.class)
class CareerRoadmapControllerTest {

    @Mock
    private GetCareerRoadmapUseCase getCareerRoadmapUseCase;

    @Mock
    private SimulateCareerMoveUseCase simulateCareerMoveUseCase;

    @Mock
    private SimulateLevelMoveUseCase simulateLevelMoveUseCase;

    private CareerRoadmapController controller;

    @BeforeEach
    void setUp() {
        controller = new CareerRoadmapController(getCareerRoadmapUseCase, simulateCareerMoveUseCase, simulateLevelMoveUseCase);
    }

    private static reactor.util.context.Context authenticatedAs(String userId) {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void simulateLevel_delegatesWithCurrentUserIdAndMapsToResponseDto() {
        LevelMoveResult result = new LevelMoveResult("Middle", "Senior", 5, 12,
                SalaryInsight.fromMidpoints("Senior", 12, List.of(30.0, 40.0), List.of()));
        when(simulateLevelMoveUseCase.execute("user-1", "Senior")).thenReturn(Mono.just(result));

        StepVerifier.create(controller.simulateLevel("Senior").contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    SimulationDtos.LevelMoveResponse body = response.getBody().getData();
                    assertThat(body.currentLevel()).isEqualTo("Middle");
                    assertThat(body.targetLevel()).isEqualTo("Senior");
                    assertThat(body.currentJobMatches()).isEqualTo(5);
                    assertThat(body.simulatedJobMatches()).isEqualTo(12);
                    assertThat(body.salary()).isNotNull();
                })
                .verifyComplete();
    }
}
