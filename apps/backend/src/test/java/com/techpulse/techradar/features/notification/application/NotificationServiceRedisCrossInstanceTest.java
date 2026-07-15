package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same proof as {@link com.techpulse.techradar.features.messaging.realtime.MessageBroadcasterRedisCrossInstanceTest},
 * for notifications: two independent {@link NotificationService} objects (standing in for two
 * backend replicas) sharing one real Redis. {@link NotificationService#save} on "instance A" must
 * reach a {@link NotificationService#streamFor} subscriber held by "instance B".
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class NotificationServiceRedisCrossInstanceTest {

    private static LettuceConnectionFactory factoryA;
    private static LettuceConnectionFactory factoryB;
    private static NotificationService instanceA;
    private static NotificationService instanceB;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        NotificationRepository stubRepository = new StubNotificationRepository();

        factoryA = newConnectionFactory(host, port);
        factoryB = newConnectionFactory(host, port);

        instanceA = newService(stubRepository, factoryA, objectMapper);
        instanceB = newService(stubRepository, factoryB, objectMapper);
    }

    @AfterAll
    static void tearDown() {
        factoryA.destroy();
        factoryB.destroy();
    }

    private static LettuceConnectionFactory newConnectionFactory(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        return factory;
    }

    private static NotificationService newService(NotificationRepository repository,
                                                    ReactiveRedisConnectionFactory connectionFactory,
                                                    ObjectMapper objectMapper) {
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(connectionFactory);
        NotificationService service = new NotificationService(repository, container, template, objectMapper);
        service.subscribeToRedis();
        return service;
    }

    /** Echoes back whatever is inserted — this test only cares about the Redis fan-out path. */
    private static final class StubNotificationRepository implements NotificationRepository {
        @Override
        public Mono<Notification> insert(Notification notification) {
            return Mono.just(notification);
        }

        @Override
        public Flux<Notification> findByUser(String userId, int limit, int offset) {
            return Flux.empty();
        }

        @Override
        public Mono<Long> countUnread(String userId) {
            return Mono.just(0L);
        }

        @Override
        public Mono<Long> markRead(String id, String userId) {
            return Mono.just(0L);
        }

        @Override
        public Mono<Long> markAllRead(String userId) {
            return Mono.just(0L);
        }

        @Override
        public Flux<com.techpulse.techradar.features.notification.domain.TrendSubscriber> findTrendSubscribers(String technology) {
            return Flux.empty();
        }

        @Override
        public Flux<com.techpulse.techradar.features.notification.domain.TrendSubscriber> findJobMatchSubscribers(
                java.util.List<String> technologies) {
            return Flux.empty();
        }

        @Override
        public Flux<NotificationRepository.TypeCount> countGroupedByType() {
            return Flux.empty();
        }
    }

    @Test
    void saveOnOneInstance_isDeliveredToAStreamSubscriberOnAnotherInstance() {
        UUID recipientId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(recipientId)
                .type("NEW_FOLLOWER")
                .title("Ai đó vừa theo dõi bạn")
                .link("/users/x")
                .read(false)
                .build();

        // "instance B" holds the recipient's SSE stream.
        Flux<Notification> received = instanceB.streamFor(recipientId.toString());

        StepVerifier.create(received.take(1).timeout(Duration.ofSeconds(5)))
                .then(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // "instance A" is where the save() request landed.
                    instanceA.save(notification).subscribe();
                })
                .assertNext(delivered -> {
                    assertThat(delivered.getUserId()).isEqualTo(recipientId);
                    assertThat(delivered.getType()).isEqualTo("NEW_FOLLOWER");
                    assertThat(delivered.getTitle()).isEqualTo("Ai đó vừa theo dõi bạn");
                })
                .verifyComplete();
    }
}
