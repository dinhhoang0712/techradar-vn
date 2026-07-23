package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.notification.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.notification.event.JobMatchEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchDispatcherTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private AlertDeliveryDispatcher alertDeliveryDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JobMatchDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new JobMatchDispatcher(repository, alertDeliveryDispatcher, objectMapper);
    }

    private ConsumerRecord<String, String> record(JobMatchEvent event) throws Exception {
        return new ConsumerRecord<>("job.match.alerts", 0, 0L, "key", objectMapper.writeValueAsString(event));
    }

    @Test
    void onJobMatch_dispatchesCurrentSkillMatchWithJobMatchType() throws Exception {
        JobMatchEvent event = new JobMatchEvent("Backend Dev", "Acme", List.of("Kotlin"), "https://x");
        UUID userId = UUID.randomUUID();
        JobMatchSubscriber sub = new JobMatchSubscriber(userId, "dev@example.com", true, false, true);
        when(repository.findJobMatchSubscribers(List.of("Kotlin"))).thenReturn(Flux.just(sub));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onJobMatch(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("JOB_MATCH"),
                eq("Việc làm mới phù hợp: Backend Dev"), any(), eq("/career?highlight=Kotlin"),
                eq(true), eq(false), eq("dev@example.com"));
    }

    @Test
    void onJobMatch_dispatchesLearningMatchWithJobMatchLearningType() throws Exception {
        JobMatchEvent event = new JobMatchEvent("Platform Eng", "Acme", List.of("Kubernetes"), "https://x");
        UUID userId = UUID.randomUUID();
        JobMatchSubscriber sub = new JobMatchSubscriber(userId, "learner@example.com", false, true, false);
        when(repository.findJobMatchSubscribers(List.of("Kubernetes"))).thenReturn(Flux.just(sub));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onJobMatch(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(userId), eq("JOB_MATCH_LEARNING"),
                eq("Việc làm cho kỹ năng bạn đang học: Platform Eng"), any(), eq("/career?highlight=Kubernetes"),
                eq(false), eq(true), eq("learner@example.com"));
    }

    @Test
    void onJobMatch_dispatchesDifferentTypesToDifferentSubscribersFromOneEvent() throws Exception {
        JobMatchEvent event = new JobMatchEvent("Full Stack Dev", null, List.of("React"), "https://x");
        UUID currentSkillUser = UUID.randomUUID();
        UUID learningUser = UUID.randomUUID();
        when(repository.findJobMatchSubscribers(List.of("React"))).thenReturn(Flux.just(
                new JobMatchSubscriber(currentSkillUser, "a@example.com", true, false, true),
                new JobMatchSubscriber(learningUser, "b@example.com", true, false, false)));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onJobMatch(record(event));

        verify(alertDeliveryDispatcher).dispatch(eq(currentSkillUser), eq("JOB_MATCH"), any(), any(), any(),
                anyBoolean(), anyBoolean(), any());
        verify(alertDeliveryDispatcher).dispatch(eq(learningUser), eq("JOB_MATCH_LEARNING"), any(), any(), any(),
                anyBoolean(), anyBoolean(), any());
    }

    @Test
    void onJobMatch_buildsHighlightLinkFromAllTechnologiesUrlEncoded() throws Exception {
        JobMatchEvent event = new JobMatchEvent("Embedded Dev", "Acme", List.of("C++", "Rust"), "https://x");
        UUID userId = UUID.randomUUID();
        JobMatchSubscriber sub = new JobMatchSubscriber(userId, "dev@example.com", true, false, true);
        when(repository.findJobMatchSubscribers(List.of("C++", "Rust"))).thenReturn(Flux.just(sub));
        when(alertDeliveryDispatcher.dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Mono.empty());

        dispatcher.onJobMatch(record(event));

        verify(alertDeliveryDispatcher).dispatch(any(), any(), any(), any(), eq("/career?highlight=C%2B%2B,Rust"),
                anyBoolean(), anyBoolean(), any());
    }

    @Test
    void onJobMatch_skipsDispatchWhenTechnologiesEmpty() throws Exception {
        JobMatchEvent event = new JobMatchEvent("No Tech Job", "Acme", List.of(), "https://x");

        dispatcher.onJobMatch(record(event));

        verifyNoInteractions(repository, alertDeliveryDispatcher);
    }

    @Test
    void onJobMatch_swallowsMalformedPayloadWithoutThrowing() {
        ConsumerRecord<String, String> malformed = new ConsumerRecord<>("job.match.alerts", 0, 0L, "key", "not json");

        dispatcher.onJobMatch(malformed);

        verifyNoInteractions(repository, alertDeliveryDispatcher);
    }

    @Test
    void onJobMatch_repositoryFailureIsSwallowed() throws Exception {
        JobMatchEvent event = new JobMatchEvent("Backend Dev", "Acme", List.of("Kotlin"), "https://x");
        when(repository.findJobMatchSubscribers(List.of("Kotlin")))
                .thenReturn(Flux.error(new RuntimeException("db down")));

        dispatcher.onJobMatch(record(event));

        verify(alertDeliveryDispatcher, never()).dispatch(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }
}
