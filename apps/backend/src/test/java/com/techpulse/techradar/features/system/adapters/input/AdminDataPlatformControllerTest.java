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
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
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
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private DataPlatformJobStatusService jobStatusService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AdminDataPlatformController controller;

    @BeforeEach
    void setUp() {
        RedisTriggerPublisher redisTriggerPublisher = new RedisTriggerPublisher(redisTemplate, objectMapper);
        controller = new AdminDataPlatformController(jobStatusService, redisTriggerPublisher, redisTemplate);
    }

    private void stubLockAcquired(String jobId) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("data-platform:trigger:lock:" + jobId), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(true));
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
        stubLockAcquired("tech_dedup");
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
        stubLockAcquired("tech_dedup");
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
        stubLockAcquired("embed_trigger");
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

    @Test
    void trigger_returnsTooManyRequests_whenDebounceLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("data-platform:trigger:lock:tech_dedup"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(false));

        StepVerifier.create(controller.trigger("tech_dedup"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("JOB_TRIGGER_DEBOUNCED");
                })
                .verifyComplete();

        verify(jobStatusService, never()).isRunning(anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void jobHistory_rejectsUnknownJobId() {
        StepVerifier.create(controller.jobHistory("bogus_job", 0, 20))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    ApiResponse<List<Map<String, Object>>> body = response.getBody();
                    assertThat(body.isSuccess()).isFalse();
                    assertThat(body.getErrorCode()).isEqualTo("UNKNOWN_JOB");
                })
                .verifyComplete();
    }

    @Test
    void jobHistory_returnsPagedRunsForKnownJob() {
        Map<String, Object> run = Map.of(
                "id", 42L, "job_name", "tech_dedup", "status", "success",
                "rows_affected", 10, "duration_s", 5.2);
        when(jobStatusService.findRunHistory("tech_dedup", 20, 0)).thenReturn(Flux.just(run));

        StepVerifier.create(controller.jobHistory("tech_dedup", 0, 20))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<List<Map<String, Object>>> body = response.getBody();
                    assertThat(body.getData()).containsExactly(run);
                })
                .verifyComplete();
    }
}
