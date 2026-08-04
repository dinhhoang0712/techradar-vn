package com.techpulse.techradar.features.job.adapters.input;

import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private GetJobMatchesUseCase getJobMatchesUseCase;

    private JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(getJobMatchesUseCase);
    }

    private static reactor.util.context.Context authenticatedAs(String userId) {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken(userId, null, List.of()));
    }

    private static JobMatch match(String title, double score) {
        return new JobMatch(title, "Acme", "Hà Nội", null, null, null, null, null, null,
                List.of("Java"), List.of("Spring"), score);
    }

    @Test
    void matches_delegatesWithCurrentUserIdAndFilters_andMapsToResponseDtos() {
        when(getJobMatchesUseCase.execute("user-1", "hà nội", 20.0, null, 5))
                .thenReturn(Flux.just(match("Backend Dev", 0.8)));

        StepVerifier.create(controller.matches("hà nội", 20.0, null, 5).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    List<JobDtos.JobMatchResponse> body = response.getBody().getData();
                    assertThat(body).hasSize(1);
                    assertThat(body.get(0).getTitle()).isEqualTo("Backend Dev");
                    assertThat(body.get(0).getScore()).isEqualTo(0.8);
                })
                .verifyComplete();

        verify(getJobMatchesUseCase).execute("user-1", "hà nội", 20.0, null, 5);
    }

    @Test
    void matches_returnsEmptyList_whenUseCaseYieldsNoMatches() {
        when(getJobMatchesUseCase.execute(eq("user-1"), eq((String) null), eq((Double) null), eq((String) null), eq(20)))
                .thenReturn(Flux.empty());

        StepVerifier.create(controller.matches(null, null, null, 20).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> assertThat(response.getBody().getData()).isEmpty())
                .verifyComplete();
    }

    @Test
    void matches_passesLevelParamThrough() {
        when(getJobMatchesUseCase.execute("user-1", null, null, "Senior", 10))
                .thenReturn(Flux.just(match("Backend Dev", 0.8)));

        StepVerifier.create(controller.matches(null, null, "Senior", 10).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> assertThat(response.getBody().getData()).hasSize(1))
                .verifyComplete();
    }
}
