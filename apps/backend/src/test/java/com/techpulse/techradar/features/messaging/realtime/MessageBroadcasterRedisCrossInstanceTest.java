package com.techpulse.techradar.features.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the single-instance SSE fan-out bug: two independent {@link
 * MessageBroadcaster} objects (standing in for two backend replicas — each with its own local
 * {@code channels} map, its own {@link ReactiveRedisMessageListenerContainer}) sharing one real
 * Redis. A publish() on "instance A" must be observable on "instance B"'s subscribe(), which was
 * impossible before this fix (publish() only ever touched its own instance's in-memory map).
 * <p>
 * Gated on REDIS_HOST since it needs a real Redis reachable, same convention as
 * {@link com.techpulse.techradar.ApiIntegrationTest}.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class MessageBroadcasterRedisCrossInstanceTest {

    private static LettuceConnectionFactory factoryA;
    private static LettuceConnectionFactory factoryB;
    private static MessageBroadcaster instanceA;
    private static MessageBroadcaster instanceB;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        factoryA = newConnectionFactory(host, port);
        factoryB = newConnectionFactory(host, port);

        instanceA = newBroadcaster(factoryA, objectMapper);
        instanceB = newBroadcaster(factoryB, objectMapper);
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

    private static MessageBroadcaster newBroadcaster(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(connectionFactory);
        MessageBroadcaster broadcaster = new MessageBroadcaster(container, template, objectMapper);
        broadcaster.subscribeToRedis();
        return broadcaster;
    }

    @Test
    void publishOnOneInstance_isDeliveredToASubscriberOnAnotherInstance() {
        String recipientUserId = "cross-instance-user-" + System.identityHashCode(this);
        DirectMessage message = new DirectMessage(
                "msg-1", "conv-1", "sender-1", "hello from instance A",
                LocalDateTime.of(2026, 7, 15, 12, 0), false);

        // "instance B" holds the recipient's SSE connection.
        Flux<DirectMessage> received = instanceB.subscribe(recipientUserId);

        StepVerifier.create(received.take(1).timeout(Duration.ofSeconds(5)))
                .then(() -> {
                    // Give the Redis subscription a moment to actually establish before publishing,
                    // since receive() subscribing and the pub/sub handshake are both async.
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // "instance A" is where the sender's request landed.
                    instanceA.publish(recipientUserId, message);
                })
                .assertNext(delivered -> assertThat(delivered).isEqualTo(message))
                .verifyComplete();
    }

    @Test
    void publish_toAUserWithNoLocalSubscriberOnEitherInstance_isANoOp() {
        DirectMessage message = new DirectMessage(
                "msg-2", "conv-1", "sender-1", "nobody is listening",
                LocalDateTime.of(2026, 7, 15, 12, 0), false);

        instanceA.publish("nobody-is-subscribed-" + System.identityHashCode(this), message);
        // No assertion beyond "this doesn't throw / hang" — absence of a subscriber must be a no-op.
    }
}
