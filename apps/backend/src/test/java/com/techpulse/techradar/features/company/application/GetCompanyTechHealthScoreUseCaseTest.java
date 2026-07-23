package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.domain.CompanyTechHealthScore;
import com.techpulse.techradar.features.kafka.ports.TechAliasResolver;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCompanyTechHealthScoreUseCaseTest {

    @Mock
    private GetCompaniesUseCase getCompaniesUseCase;

    @Mock
    private RadarQueryRepository radarQueryRepository;

    @Mock
    private TechAliasResolver techAliasCache;

    private GetCompanyTechHealthScoreUseCase useCase;

    private static CompanyProfile profile(String id, List<String> techStack) {
        return new CompanyProfile(id, "Acme Corp", "Hà Nội", techStack, 3, "Fintech", "100-500");
    }

    @Test
    void execute_whenCompanyNotFound_errorsWithNotFoundException() {
        useCase = new GetCompanyTechHealthScoreUseCase(getCompaniesUseCase, radarQueryRepository, techAliasCache);
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(profile("id-1", List.of("Java"))));

        StepVerifier.create(useCase.execute("missing-id"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void execute_withEmptyTechStack_shortCircuitsWithoutCallingRepository() {
        useCase = new GetCompanyTechHealthScoreUseCase(getCompaniesUseCase, radarQueryRepository, techAliasCache);
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(profile("id-1", List.of())));

        StepVerifier.create(useCase.execute("id-1"))
                .assertNext(score -> {
                    assertThat(score.available()).isFalse();
                    assertThat(score.stackSize()).isZero();
                })
                .verifyComplete();

        verify(radarQueryRepository, never()).findLatestSnapshotsForNames(anyList());
    }

    @Test
    void execute_resolvesAliasesAndDedupesBeforeQueryingRadarRepository() {
        useCase = new GetCompanyTechHealthScoreUseCase(getCompaniesUseCase, radarQueryRepository, techAliasCache);
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(profile("id-1", List.of("golang", "Golang", "React"))));
        when(techAliasCache.resolve("golang")).thenReturn("Go");
        when(techAliasCache.resolve("Golang")).thenReturn("Go");
        when(techAliasCache.resolve("React")).thenReturn("React");
        when(radarQueryRepository.findLatestSnapshotsForNames(List.of("go", "react")))
                .thenReturn(Flux.just(new TechSnapshot("Go", 100, 20, 20, 100)));

        StepVerifier.create(useCase.execute("id-1"))
                .assertNext(score -> {
                    assertThat(score.available()).isTrue();
                    assertThat(score.stackSize()).isEqualTo(3);
                    assertThat(score.trackedCount()).isEqualTo(1);
                })
                .verifyComplete();

        verify(radarQueryRepository).findLatestSnapshotsForNames(List.of("go", "react"));
    }
}
