package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.kafka.adapters.input.KafkaNeo4jWriterService;
import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineHealthServiceTest {

    @Mock
    private KafkaNeo4jWriterService kafkaNeo4jWriterService;

    private PipelineHealthService service;

    @BeforeEach
    void setUp() {
        service = new PipelineHealthService(kafkaNeo4jWriterService);
    }

    @Test
    void pipelineHealth_delegatesToKafkaNeo4jWriterService() {
        KafkaSyncStatus status = new KafkaSyncStatus(10L, 1L, 8L, 0L, Instant.now(), Instant.now(), null, null);
        when(kafkaNeo4jWriterService.syncStatus()).thenReturn(status);

        assertThat(service.pipelineHealth()).isEqualTo(status);
    }
}
