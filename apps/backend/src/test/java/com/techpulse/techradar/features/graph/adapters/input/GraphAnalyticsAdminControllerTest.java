package com.techpulse.techradar.features.graph.adapters.input;

import com.techpulse.techradar.features.graph.application.RebuildGraphAnalyticsUseCase;
import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphAnalyticsAdminControllerTest {

    @Mock
    private RebuildGraphAnalyticsUseCase rebuildGraphAnalyticsUseCase;
    @Mock
    private AuditLogService auditLogService;

    private GraphAnalyticsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new GraphAnalyticsAdminController(rebuildGraphAnalyticsUseCase, auditLogService);
        lenient().when(auditLogService.record(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void rebuild_returnsSummary_on200() {
        when(rebuildGraphAnalyticsUseCase.execute()).thenReturn(Mono.just(new GraphAnalyticsSummary(120, 9)));

        StepVerifier.create(controller.rebuild())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<GraphAnalyticsSummary> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData().technologiesScored()).isEqualTo(120);
                    assertThat(body.getData().communitiesFound()).isEqualTo(9);
                })
                .verifyComplete();

        verify(auditLogService).record(eq("GRAPH_ANALYTICS_REBUILD"), eq("graph"), any(), any());
    }

    @Test
    void rebuild_surfacesFailure_as503() {
        when(rebuildGraphAnalyticsUseCase.execute())
                .thenReturn(Mono.error(new RuntimeException("gds.pageRank.stream not found")));

        StepVerifier.create(controller.rebuild())
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(503))
                .verifyComplete();
    }
}
