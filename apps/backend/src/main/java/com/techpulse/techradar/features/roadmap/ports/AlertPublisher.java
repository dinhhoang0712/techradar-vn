package com.techpulse.techradar.features.roadmap.ports;

import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;

/**
 * Output port for publishing roadmap alert events, so {@code RoadmapAlertService} (the weekly
 * scan that decides *whether* a user should be alerted) doesn't need to know *how* that alert is
 * delivered (currently Kafka, via {@code features.kafka.adapters.output.KafkaProducerService}).
 */
public interface AlertPublisher {

    /**
     * @throws RuntimeException if the event could not be published; callers decide how to
     *         handle/log that (see {@code RoadmapAlertService#publish}).
     */
    void publish(RoadmapAlertEvent event);
}
