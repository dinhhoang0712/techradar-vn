package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.domain.SimilarCompany;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSimilarCompaniesUseCaseTest {

    @Mock
    private GetCompaniesUseCase getCompaniesUseCase;

    private GetSimilarCompaniesUseCase useCase;

    private static CompanyProfile profile(String id, List<String> techStack) {
        return new CompanyProfile(id, "Company " + id, "Hà Nội", techStack, techStack.size(), "Tech", "100-500");
    }

    @BeforeEach
    void setUp() {
        useCase = new GetSimilarCompaniesUseCase(getCompaniesUseCase);
    }

    @Test
    void execute_fails_whenTargetCompanyNotFound() {
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(profile("a", List.of("Java"))));

        StepVerifier.create(useCase.execute("missing-id", 10))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void execute_ranksCandidatesByJaccardSimilarityDescending() {
        CompanyProfile target = profile("target", List.of("Java", "Spring", "Postgres"));
        CompanyProfile highOverlap = profile("high", List.of("Java", "Spring", "Redis"));
        CompanyProfile lowOverlap = profile("low", List.of("Java", "Go", "Rust"));
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(target, highOverlap, lowOverlap));

        StepVerifier.create(useCase.execute("target", 10))
                .assertNext(ranked -> {
                    assertThat(ranked).hasSize(2);
                    assertThat(ranked.get(0).id()).isEqualTo("high");
                    assertThat(ranked.get(1).id()).isEqualTo("low");
                    assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(1).score());
                })
                .verifyComplete();
    }

    @Test
    void execute_excludesTargetCompanyItselfFromResults() {
        CompanyProfile target = profile("target", List.of("Java"));
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(target));

        StepVerifier.create(useCase.execute("target", 10))
                .assertNext(ranked -> assertThat(ranked).isEmpty())
                .verifyComplete();
    }

    @Test
    void execute_excludesCandidatesWithZeroSharedTech() {
        CompanyProfile target = profile("target", List.of("Java"));
        CompanyProfile noOverlap = profile("no-overlap", List.of("Python", "Django"));
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(target, noOverlap));

        StepVerifier.create(useCase.execute("target", 10))
                .assertNext(ranked -> assertThat(ranked).isEmpty())
                .verifyComplete();
    }

    @Test
    void execute_matchesTechNamesCaseInsensitively() {
        CompanyProfile target = profile("target", List.of("java", "Spring"));
        CompanyProfile candidate = profile("candidate", List.of("JAVA", "spring"));
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(target, candidate));

        StepVerifier.create(useCase.execute("target", 10))
                .assertNext(ranked -> {
                    assertThat(ranked).hasSize(1);
                    assertThat(ranked.get(0).sharedTechs()).containsExactlyInAnyOrder("JAVA", "spring");
                    assertThat(ranked.get(0).score()).isEqualTo(1.0);
                })
                .verifyComplete();
    }

    @Test
    void execute_defaultsLimitToTen_whenNonPositive() {
        CompanyProfile target = profile("target", List.of("Java"));
        List<CompanyProfile> candidates = new java.util.ArrayList<>();
        candidates.add(target);
        for (int i = 0; i < 15; i++) {
            candidates.add(profile("c" + i, List.of("Java")));
        }
        when(getCompaniesUseCase.all()).thenReturn(Flux.fromIterable(candidates));

        StepVerifier.create(useCase.execute("target", 0))
                .assertNext(ranked -> assertThat(ranked).hasSize(10))
                .verifyComplete();
    }

    @Test
    void execute_clampsLimitToOneHundred() {
        CompanyProfile target = profile("target", List.of("Java"));
        List<CompanyProfile> candidates = new java.util.ArrayList<>();
        candidates.add(target);
        for (int i = 0; i < 150; i++) {
            candidates.add(profile("c" + i, List.of("Java")));
        }
        when(getCompaniesUseCase.all()).thenReturn(Flux.fromIterable(candidates));

        StepVerifier.create(useCase.execute("target", 999))
                .assertNext(ranked -> assertThat(ranked).hasSize(100))
                .verifyComplete();
    }

    @Test
    void execute_returnsSharedTechsInCandidatesOriginalCasing() {
        CompanyProfile target = profile("target", List.of("java", "spring"));
        CompanyProfile candidate = profile("candidate", List.of("Java", "React"));
        when(getCompaniesUseCase.all()).thenReturn(Flux.just(target, candidate));

        StepVerifier.create(useCase.execute("target", 10))
                .assertNext(ranked -> assertThat(ranked.get(0).sharedTechs()).containsExactly("Java"))
                .verifyComplete();
    }
}
