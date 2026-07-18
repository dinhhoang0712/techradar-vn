package com.techpulse.techradar.features.system.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.system.application.DataPlatformJobStatusService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDataPlatformControllerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private DataPlatformJobStatusService jobStatusService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AdminDataPlatformController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminDataPlatformController(redisTemplate, jobStatusService, objectMapper);
    }

    @Test
    void listJobs_fillsNeverRun_forJobsMissingFromPipelineRuns() {
        when(jobStatusService.findLatestStatuses(anyList())).thenReturn(Flux.just(
                Map.of("job_name", "neo4j_article_sync", "status", "success"),
                Map.of("job_name", "tech_dedup", "status", "failed")
        ));

        StepVerifier.create(controller.listJobs())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    List<Map<String, Object>> jobs = response.getBody().getData();
                    assertThat(jobs).hasSize(5);
                    assertThat(jobs).extracting(j -> j.get("job_name")).containsExactly(
                            "neo4j_article_sync", "neo4j_job_sync", "neo4j_enricher",
                            "tech_dedup", "embed_trigger");

                    Map<String, Object> articleSync = jobs.get(0);
                    assertThat(articleSync.get("status")).isEqualTo("success");

                    Map<String, Object> jobSync = jobs.get(1);
                    assertThat(jobSync.get("status")).isEqualTo("never_run");
                })
                .verifyComplete();
    }

    @Test
    void trigger_rejectsUnknownJobId() {
        StepVerifier.create(controller.trigger("bogus_job"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("UNKNOWN_JOB");
                })
                .verifyComplete();

        verify(jobStatusService, never()).isRunning(anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void trigger_returnsConflict_whenJobAlreadyRunning() {
        when(jobStatusService.isRunning("tech_dedup")).thenReturn(Mono.just(true));

        StepVerifier.create(controller.trigger("tech_dedup"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("DATA_PLATFORM_JOB_RUNNING");
                })
                .verifyComplete();

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void trigger_publishesAndReportsDelivered_whenIdleAndSubscriberPresent() {
        when(jobStatusService.isRunning("tech_dedup")).thenReturn(Mono.just(false));
        when(redisTemplate.convertAndSend(eq("data-platform:trigger"), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(controller.trigger("tech_dedup"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsEntry("delivered", true);
                })
                .verifyComplete();
    }

    @Test
    void trigger_publishesButReportsNotDelivered_whenNoSubscriberIsListening() {
        when(jobStatusService.isRunning("embed_trigger")).thenReturn(Mono.just(false));
        when(redisTemplate.convertAndSend(eq("data-platform:trigger"), anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(controller.trigger("embed_trigger"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body.getData()).containsEntry("delivered", false);
                })
                .verifyComplete();
    }
}
