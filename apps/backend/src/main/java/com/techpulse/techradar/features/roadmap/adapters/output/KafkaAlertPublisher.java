package com.techpulse.techradar.features.roadmap.adapters.output;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;
import com.techpulse.techradar.features.roadmap.ports.AlertPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Kafka-backed {@link AlertPublisher}: publishes to the {@value KafkaTopicConstants#ROADMAP_ALERTS} topic. */
@Component
@RequiredArgsConstructor
public class KafkaAlertPublisher implements AlertPublisher {

    private final KafkaProducerService kafkaProducer;

    @Override
    public void publish(RoadmapAlertEvent event) {
        kafkaProducer.send(KafkaTopicConstants.ROADMAP_ALERTS, event);
    }
}
