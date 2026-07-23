package com.techpulse.techradar.features.social.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Same proof as {@link com.techpulse.techradar.features.notification.application.NotificationServiceRedisCrossInstanceTest},
 * {@link com.techpulse.techradar.features.messaging.realtime.MessageBroadcasterRedisCrossInstanceTest} and
 * {@link com.techpulse.techradar.features.radar.realtime.RadarBroadcasterRedisCrossInstanceTest}, for the social feed:
 * two independent {@link FeedBroadcaster} objects (standing in for two backend replicas — each with its own
 * local {@link reactor.core.publisher.Sinks.Many} and its own {@link ReactiveRedisMessageListenerContainer})
 * sharing one real Redis. A {@code publishPostCreated}/{@code publishLike} call on "instance A" (the replica
 * that handled the write) must reach a {@link FeedBroadcaster#streamFor} subscriber held by "instance B" (a
 * replica just serving a viewer's SSE feed connection).
 * <p>
 * Gated on REDIS_HOST since it needs a real Redis reachable, same convention as
 * {@link com.techpulse.techradar.integration.IntegrationTestSupport}.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class FeedBroadcasterRedisCrossInstanceTest {

    private static LettuceConnectionFactory factoryA;
    private static LettuceConnectionFactory factoryB;
    private static FeedBroadcaster instanceA;
    private static FeedBroadcaster instanceB;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Neither test below exercises a POST_CREATED event under "following" scope from a different
        // author, so the follow graph is never actually consulted; a bare mock is enough to satisfy
        // FeedBroadcaster's constructor.
        FollowRepository followRepository = mock(FollowRepository.class);

        factoryA = newConnectionFactory(host, port);
        factoryB = newConnectionFactory(host, port);

        instanceA = newBroadcaster(followRepository, factoryA, objectMapper);
        instanceB = newBroadcaster(followRepository, factoryB, objectMapper);
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

    private static FeedBroadcaster newBroadcaster(FollowRepository followRepository,
                                                   ReactiveRedisConnectionFactory connectionFactory,
                                                   ObjectMapper objectMapper) {
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(connectionFactory);
        FeedBroadcaster broadcaster = new FeedBroadcaster(container, template, objectMapper, followRepository);
        broadcaster.subscribeToRedis();
        return broadcaster;
    }

    private static FeedPost samplePost(UUID authorId) {
        return new FeedPost(
                UUID.randomUUID().toString(),
                new UserSummary(authorId.toString(), "Cross Instance Author", null),
                "hello from instance A",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                0, 0, false, List.of(), List.of(), null);
    }

    @Test
    void publishPostCreatedOnOneInstance_isDeliveredToAnExploreStreamSubscriberOnAnotherInstance() {
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        FeedPost post = samplePost(authorId);

        // "instance B" holds the viewer's SSE feed connection, scoped to "explore" so it sees every post.
        Flux<FeedEvent> received = instanceB.streamFor(viewerId.toString(), "explore");

        StepVerifier.create(received.take(1).timeout(Duration.ofSeconds(5)))
                .then(() -> {
                    // Give the Redis subscription a moment to actually establish before publishing,
                    // since receive() subscribing and the pub/sub handshake are both async.
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // "instance A" is where the post-creation request landed.
                    instanceA.publishPostCreated(post);
                })
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(FeedEvent.Type.POST_CREATED);
                    assertThat(event.postId()).isEqualTo(post.id());
                    assertThat(event.post()).isEqualTo(post);
                })
                .verifyComplete();
    }

    @Test
    void publishLikeOnOneInstance_isDeliveredToAFollowingStreamSubscriberOnAnotherInstance() {
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        String postId = UUID.randomUUID().toString();

        // Like events always pass through streamFor()'s filter regardless of scope or follow status,
        // so "following" scope here still must deliver without ever calling out to followRepository.
        Flux<FeedEvent> received = instanceB.streamFor(viewerId.toString(), "following");

        StepVerifier.create(received.take(1).timeout(Duration.ofSeconds(5)))
                .then(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    instanceA.publishLike(postId, authorId, 7L);
                })
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(FeedEvent.Type.POST_LIKED);
                    assertThat(event.postId()).isEqualTo(postId);
                    assertThat(event.authorId()).isEqualTo(authorId);
                    assertThat(event.likeCount()).isEqualTo(7L);
                })
                .verifyComplete();
    }

    @Test
    void publish_withNoLocalSubscriberOnEitherInstance_isANoOp() {
        FeedPost post = samplePost(UUID.randomUUID());

        instanceA.publishPostCreated(post);
        // No assertion beyond "this doesn't throw / hang" — a publish with nobody streaming must be a no-op.
    }
}
