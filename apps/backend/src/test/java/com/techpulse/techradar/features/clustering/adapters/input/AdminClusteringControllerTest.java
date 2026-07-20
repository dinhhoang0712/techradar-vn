package com.techpulse.techradar.features.clustering.adapters.input;

import com.techpulse.techradar.features.clustering.application.GetPipelineRunsUseCase;
import com.techpulse.techradar.features.clustering.application.GetPipelineStatusUseCase;
import com.techpulse.techradar.features.clustering.application.TriggerPipelineUseCase;
import com.techpulse.techradar.features.clustering.application.UpdateClusterLabelUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminClusteringControllerTest {

    @Mock
    private GetPipelineStatusUseCase getPipelineStatusUseCase;
    @Mock
    private TriggerPipelineUseCase triggerPipelineUseCase;
    @Mock
    private GetPipelineRunsUseCase getPipelineRunsUseCase;
    @Mock
    private UpdateClusterLabelUseCase updateClusterLabelUseCase;

    private AdminClusteringController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminClusteringController(
                getPipelineStatusUseCase, triggerPipelineUseCase, getPipelineRunsUseCase, updateClusterLabelUseCase);
    }

    @Test
    void pipelineStatus_returnsLiveStateFromUseCase() {
        when(getPipelineStatusUseCase.execute())
                .thenReturn(Mono.just(Map.of("status", "running", "current_stage", "pipelines.stage_02_features")));

        StepVerifier.create(controller.pipelineStatus())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).containsEntry("status", "running");
                })
                .verifyComplete();
    }

    @Test
    void triggerPipeline_surfacesConflict_whenAlreadyRunning() {
        when(triggerPipelineUseCase.execute())
                .thenReturn(Mono.error(new ConflictException(ErrorCode.PIPELINE_RUNNING, "Đang chạy")));

        StepVerifier.create(controller.triggerPipeline())
                .expectErrorMatches(ex -> ex instanceof ConflictException
                        && ((ConflictException) ex).getStatusCode() == 409)
                .verify();
    }

    @Test
    void pipelineRuns_collectsFluxIntoListResponse() {
        when(getPipelineRunsUseCase.execute()).thenReturn(Flux.just(
                Map.of("run_id", "run-1", "metrics", Map.of("silhouette", 0.4)),
                Map.of("run_id", "run-2", "metrics", Map.of("silhouette", 0.5))
        ));

        StepVerifier.create(controller.pipelineRuns())
                .assertNext(response -> {
                    ApiResponse<List<Map<String, Object>>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void updateClusterLabel_mapsRequestFieldsAndReturnsUpdatedCluster() {
        AdminClusteringController.UpdateClusterLabelRequest request = new AdminClusteringController.UpdateClusterLabelRequest();
        request.setLabel("Django Ecosystem");
        request.setLabelEn("Django Ecosystem EN");

        when(updateClusterLabelUseCase.execute(
                eq("0"), eq("Django Ecosystem"), eq("Django Ecosystem EN"), isNull(), isNull(), any()
        )).thenReturn(Mono.just(Map.of("cluster_id", 0, "label", "Django Ecosystem", "overridden", true)));

        StepVerifier.create(controller.updateClusterLabel("0", request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).containsEntry("overridden", true);
                })
                .verifyComplete();
    }

    @Test
    void updateClusterLabel_surfacesNotFound_whenClusterUnknown() {
        AdminClusteringController.UpdateClusterLabelRequest request = new AdminClusteringController.UpdateClusterLabelRequest();
        request.setLabel("X");

        when(updateClusterLabelUseCase.execute(eq("999"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new NotFoundException("Cluster 999 không tồn tại")));

        StepVerifier.create(controller.updateClusterLabel("999", request))
                .expectErrorMatches(ex -> ex instanceof NotFoundException)
                .verify();
    }

    @Test
    void updateClusterLabel_rejectsEmptyBody_asBadRequest() {
        AdminClusteringController.UpdateClusterLabelRequest request = new AdminClusteringController.UpdateClusterLabelRequest();

        when(updateClusterLabelUseCase.execute(eq("0"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalArgumentException("At least one of label/labelEn/description/domain is required")));

        StepVerifier.create(controller.updateClusterLabel("0", request))
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(400))
                .verifyComplete();
    }
}
