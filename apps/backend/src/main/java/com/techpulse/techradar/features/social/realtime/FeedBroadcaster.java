package com.techpulse.techradar.features.social.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.UUID;

/**
 * Live feed delivery over SSE, mirroring
 * {@link com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster} and
 * {@link com.techpulse.techradar.features.notification.application.NotificationService}: publish()
 * always goes over Redis Pub/Sub (channel {@value #CHANNEL}) so every backend instance — not just the
 * one that handled the write — feeds its own local {@link Sinks.Many} and can deliver to whichever SSE
 * clients ({@link #streamFor}) are connected to it. Fire-and-forget by design, same as the in-memory
 * sink this replaced: Postgres remains the source of truth, a missed live push just means the viewer
 * sees it on their next {@code GET /feed} instead of instantly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedBroadcaster {

    private static final String CHANNEL = "live:feed";
    private static final RedisSerializationContext.SerializationPair<String> STRING_PAIR =
            RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string());

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FollowRepository followRepository;

    private final Sinks.Many<FeedEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    @PostConstruct
    void subscribeToRedis() {
        redisListenerContainer.receive(List.of(ChannelTopic.of(CHANNEL)), STRING_PAIR, STRING_PAIR)
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, FeedEvent.class))
                        .onErrorResume(e -> {
                            log.warn("Could not parse live feed event from Redis", e);
                            return Mono.empty();
                        }))
                .doOnNext(sink::tryEmitNext)
                .subscribe();
    }

    /**
     * Subscribes to the live feed, scoped like {@code GET /feed}: "explore" sees every post; anything
     * else (default "following") only sees new posts from the viewer or people they follow. Like/comment
     * updates always pass through — the client only applies them to posts it already has loaded.
     */
    public Flux<FeedEvent> streamFor(String viewerId, String scope) {
        UUID viewerUuid = UUID.fromString(viewerId);
        return sink.asFlux().filterWhen(event -> shouldDeliver(event, viewerUuid, scope));
    }

    Mono<Boolean> shouldDeliver(FeedEvent event, UUID viewerId, String scope) {
        if ("explore".equals(scope) || event.type() != FeedEvent.Type.POST_CREATED) {
            return Mono.just(true);
        }
        if (viewerId.equals(event.authorId())) {
            return Mono.just(true);
        }
        return followRepository.isFollowing(viewerId, event.authorId());
    }

    public void publishPostCreated(FeedPost post) {
        publish(FeedEvent.postCreated(post));
    }

    public void publishLike(String postId, UUID authorId, long likeCount) {
        publish(FeedEvent.postLiked(postId, authorId, likeCount));
    }

    public void publishComment(String postId, UUID authorId, long commentCount) {
        publish(FeedEvent.commentAdded(postId, authorId, commentCount));
    }

    private void publish(FeedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(CHANNEL, json)
                    .doOnError(e -> log.warn("Failed to publish live feed event for post {}", event.postId(), e))
                    .subscribe();
        } catch (Exception e) {
            log.warn("Failed to serialize live feed event for post {}", event.postId(), e);
        }
    }
}
