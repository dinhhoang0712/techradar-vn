package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyInsightControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;

    private CompanyInsightController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyInsightController(new AiProxyRequestHandler(aiProxyPort));
    }

    @Test
    void insight_forwardsBodyAsIsAndWrapsTheProxyResponse() {
        Map<String, Object> body = Map.of("company_name", "Acme Corp");
        Map<String, Object> proxyResponse = Map.of("summary", "Acme đang mở rộng đội backend.", "highlights", java.util.List.of());
        when(aiProxyPort.forward(eq("/company-insight"), eq(body), any(Duration.class)))
                .thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.insight(body))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse).isNotNull();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();
    }

    @Test
    void insight_returns503WhenThePythonServiceIsUnavailable() {
        Map<String, Object> body = Map.of("company_name", "Acme Corp");
        when(aiProxyPort.forward(eq("/company-insight"), eq(body), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.insight(body))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody().isSuccess()).isFalse();
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }
}
