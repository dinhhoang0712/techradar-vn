package com.techpulse.techradar.features.salary.adapters.input;

import com.techpulse.techradar.features.salary.application.GetSalaryInsightsUseCase;
import com.techpulse.techradar.features.salary.application.GetTechSalaryDetailUseCase;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.NotFoundException;
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
class SalaryControllerTest {

    @Mock
    private GetSalaryInsightsUseCase getSalaryInsightsUseCase;

    @Mock
    private GetTechSalaryDetailUseCase getTechSalaryDetailUseCase;

    private SalaryController controller;

    @BeforeEach
    void setUp() {
        controller = new SalaryController(getSalaryInsightsUseCase, getTechSalaryDetailUseCase);
    }

    private static SalaryInsight insight(String tech) {
        return new SalaryInsight(tech, 3, 2, 20.0, 21.0, 15.0, 25.0, 18.0, 22.0, List.of("Spring"));
    }

    @Test
    void getTop_delegatesToUseCaseAndMapsInsightsToResponseDtos() {
        when(getSalaryInsightsUseCase.execute(40, 1)).thenReturn(Flux.just(insight("Java")));

        StepVerifier.create(controller.getTop(40, 1))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<SalaryDtos.SalaryInsightResponse>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getTechName()).isEqualTo("Java");
                    assertThat(body.getData().get(0).getMedianSalaryMVnd()).isEqualTo(20.0);
                })
                .verifyComplete();

        verify(getSalaryInsightsUseCase).execute(40, 1);
    }

    @Test
    void getTop_returnsEmptyListWhenNoTechClearsTheThreshold() {
        when(getSalaryInsightsUseCase.execute(40, 1)).thenReturn(Flux.empty());

        StepVerifier.create(controller.getTop(40, 1))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void getTechSalary_returnsDetailOnSuccess() {
        when(getTechSalaryDetailUseCase.execute("Java")).thenReturn(Mono.just(insight("Java")));

        StepVerifier.create(controller.getTechSalary("Java"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getTechSalary_returns404WhenTechnologyNotFound() {
        when(getTechSalaryDetailUseCase.execute("Cobol"))
                .thenReturn(Mono.error(new NotFoundException("Technology not found: Cobol")));

        StepVerifier.create(controller.getTechSalary("Cobol"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    @SuppressWarnings("unchecked")
                    ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("NOT_FOUND");
                    assertThat(body.getMessage()).isEqualTo("Technology not found: Cobol");
                })
                .verifyComplete();
    }
}
