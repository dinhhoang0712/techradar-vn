package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.ports.PipelineRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlatformJobStatusServiceTest {

    @Mock
    private PipelineRunRepository pipelineRunRepository;

    private DataPlatformJobStatusService service;

    @BeforeEach
    void setUp() {
        service = new DataPlatformJobStatusService(pipelineRunRepository);
    }

    @Test
    void findRunHistory_delegatesToRepositoryWithSameArguments() {
        Map<String, Object> run = Map.of("id", 1L, "job_name", "tech_dedup", "status", "success", "duration_s", 12.5);
        when(pipelineRunRepository.findRunHistory("tech_dedup", 20, 40)).thenReturn(Flux.just(run));

        StepVerifier.create(service.findRunHistory("tech_dedup", 20, 40))
                .expectNext(run)
                .verifyComplete();

        verify(pipelineRunRepository).findRunHistory("tech_dedup", 20, 40);
    }

    @Test
    void isRunning_stillDelegatesToFindLatestStatuses_unaffectedByHistoryAddition() {
        when(pipelineRunRepository.findLatestStatuses(List.of("neo4j_enricher")))
                .thenReturn(Flux.just(Map.of("job_name", "neo4j_enricher", "status", "running")));

        StepVerifier.create(service.isRunning("neo4j_enricher"))
                .expectNext(true)
                .verifyComplete();

        verify(pipelineRunRepository).findLatestStatuses(eq(List.of("neo4j_enricher")));
    }
}
