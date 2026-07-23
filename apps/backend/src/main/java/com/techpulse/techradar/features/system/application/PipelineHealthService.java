package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.kafka.adapters.input.KafkaNeo4jWriterService;
import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kafka-to-Neo4j data pipeline health for the admin dashboard.
 */
@Component
@RequiredArgsConstructor
public class PipelineHealthService {

    private final KafkaNeo4jWriterService kafkaNeo4jWriterService;

    public KafkaSyncStatus pipelineHealth() {
        return kafkaNeo4jWriterService.syncStatus();
    }
}
