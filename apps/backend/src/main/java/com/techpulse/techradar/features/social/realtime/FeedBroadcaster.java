package com.techpulse.techradar.features.social.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.FeedScope;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.redis.RedisFanout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

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

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FollowRepository followRepository;

    // autoCancel=false: if every viewer disconnects momentarily (e.g. a quiet overnight period),
    // the default autoCancel=true would terminate this sink and silently refuse every subscriber
    // for the rest of the process's lifetime.
    private final Sinks.Many<FeedEvent> sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    @PostConstruct
    void subscribeToRedis() {
        RedisFanout.subscribe(redisListenerContainer, objectMapper, CHANNEL, FeedEvent.class, sink::tryEmitNext);
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
        if (FeedScope.fromParam(scope) == FeedScope.EXPLORE || event.type() != FeedEvent.Type.POST_CREATED) {
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
        RedisFanout.publish(redisTemplate, objectMapper, CHANNEL, event);
    }
}
