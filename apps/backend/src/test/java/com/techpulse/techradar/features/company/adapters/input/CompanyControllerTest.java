package com.techpulse.techradar.features.company.adapters.input;

import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.company.application.GetCompanyMentionsUseCase;
import com.techpulse.techradar.features.company.application.GetCompanyTechHealthScoreUseCase;
import com.techpulse.techradar.features.company.application.GetSimilarCompaniesUseCase;
import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.domain.CompanyTechHealthScore;
import com.techpulse.techradar.features.company.domain.SimilarCompany;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private GetCompaniesUseCase getCompaniesUseCase;

    @Mock
    private GetSimilarCompaniesUseCase getSimilarCompaniesUseCase;

    @Mock
    private GetCompanyMentionsUseCase getCompanyMentionsUseCase;

    @Mock
    private GetCompanyTechHealthScoreUseCase getCompanyTechHealthScoreUseCase;

    private CompanyController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyController(getCompaniesUseCase, getSimilarCompaniesUseCase, getCompanyMentionsUseCase,
                getCompanyTechHealthScoreUseCase);
    }

    private static CompanyProfile profile(String id, String industry, String size) {
        return new CompanyProfile(id, "Acme Corp", "Hà Nội", List.of("Java"), 3, industry, size);
    }

    @Test
    void list_mapsIndustryAndSizeOntoTheResponseDto() {
        when(getCompaniesUseCase.execute("acme", 0, 20)).thenReturn(Flux.just(profile("id-1", "Fintech", "100-500")));

        StepVerifier.create(controller.list("acme", 0, 20))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<CompanyDtos.CompanyProfileResponse>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getIndustry()).isEqualTo("Fintech");
                    assertThat(body.getData().get(0).getSize()).isEqualTo("100-500");
                })
                .verifyComplete();

        verify(getCompaniesUseCase).execute("acme", 0, 20);
    }

    @Test
    void list_allowsNullIndustryAndSize() {
        when(getCompaniesUseCase.execute(null, 0, 20)).thenReturn(Flux.just(profile("id-1", null, null)));

        StepVerifier.create(controller.list(null, 0, 20))
                .assertNext(response -> {
                    assertThat(response.getBody().getData().get(0).getIndustry()).isNull();
                    assertThat(response.getBody().getData().get(0).getSize()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void similar_delegatesToUseCaseAndMapsToResponseDtos() {
        SimilarCompany similar = new SimilarCompany("id-2", "Beta Inc", "Đà Nẵng", List.of("Java"), 0.5);
        when(getSimilarCompaniesUseCase.execute("id-1", 10)).thenReturn(Mono.just(List.of(similar)));

        StepVerifier.create(controller.similar("id-1", 10))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).hasSize(1);
                    assertThat(response.getBody().getData().get(0).getName()).isEqualTo("Beta Inc");
                })
                .verifyComplete();
    }

    @Test
    void mentions_delegatesToUseCaseAndMapsToResponseDtos() {
        CompanyMention mention = new CompanyMention("a-1", "Acme raises Series B", "https://example.com/a-1",
                "2026-06-01", "VNExpress");
        when(getCompanyMentionsUseCase.execute("id-1", 5)).thenReturn(Flux.just(mention));

        StepVerifier.create(controller.mentions("id-1", 5))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<CompanyDtos.CompanyMentionResponse>> body = response.getBody();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getTitle()).isEqualTo("Acme raises Series B");
                    assertThat(body.getData().get(0).getSourcePlatform()).isEqualTo("VNExpress");
                })
                .verifyComplete();

        verify(getCompanyMentionsUseCase).execute("id-1", 5);
    }

    @Test
    void mentions_returnsEmptyListWhenNoArticlesMentionTheCompany() {
        when(getCompanyMentionsUseCase.execute("id-1", 5)).thenReturn(Flux.empty());

        StepVerifier.create(controller.mentions("id-1", 5))
                .assertNext(response -> assertThat(response.getBody().getData()).isEmpty())
                .verifyComplete();
    }

    @Test
    void healthScore_delegatesToUseCaseAndMapsToResponseDto() {
        CompanyTechHealthScore score = new CompanyTechHealthScore(
                true, 78, "Đang bắt kịp xu hướng công nghệ", 5, 3, List.of("Kubernetes"), List.of());
        when(getCompanyTechHealthScoreUseCase.execute("id-1")).thenReturn(Mono.just(score));

        StepVerifier.create(controller.healthScore("id-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<CompanyDtos.CompanyTechHealthScoreResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData().isAvailable()).isTrue();
                    assertThat(body.getData().getScore()).isEqualTo(78);
                    assertThat(body.getData().getStrengths()).containsExactly("Kubernetes");
                })
                .verifyComplete();

        verify(getCompanyTechHealthScoreUseCase).execute("id-1");
    }
}
