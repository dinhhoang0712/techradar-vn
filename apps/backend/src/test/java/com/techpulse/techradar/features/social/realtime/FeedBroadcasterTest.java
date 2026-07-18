package com.techpulse.techradar.features.social.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedBroadcasterTest {

    @Mock
    private ReactiveRedisMessageListenerContainer redisListenerContainer;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private FollowRepository followRepository;

    private FeedBroadcaster broadcaster;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        broadcaster = new FeedBroadcaster(redisListenerContainer, redisTemplate, new ObjectMapper(), followRepository);
    }

    private static FeedPost samplePost(UUID authorId) {
        return new FeedPost(
                UUID.randomUUID().toString(),
                new UserSummary(authorId.toString(), "Test User", null),
                "Hello world",
                LocalDateTime.now(),
                0, 0, false, List.of(), List.of(), null);
    }

    @Test
    void shouldDeliver_exploreScopeAlwaysPassesNewPosts() {
        FeedEvent event = FeedEvent.postCreated(samplePost(authorId));

        StepVerifier.create(broadcaster.shouldDeliver(event, viewerId, "explore"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldDeliver_likeAndCommentEventsAlwaysPassRegardlessOfScopeOrFollowStatus() {
        FeedEvent like = FeedEvent.postLiked("post-1", authorId, 5);
        FeedEvent comment = FeedEvent.commentAdded("post-1", authorId, 3);

        StepVerifier.create(broadcaster.shouldDeliver(like, viewerId, "following")).expectNext(true).verifyComplete();
        StepVerifier.create(broadcaster.shouldDeliver(comment, viewerId, "following")).expectNext(true).verifyComplete();
    }

    @Test
    void shouldDeliver_followingScopeAlwaysPassesYourOwnNewPost() {
        FeedEvent event = FeedEvent.postCreated(samplePost(viewerId));

        StepVerifier.create(broadcaster.shouldDeliver(event, viewerId, "following"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldDeliver_followingScopePassesNewPostsFromFollowedAuthors() {
        when(followRepository.isFollowing(viewerId, authorId)).thenReturn(Mono.just(true));
        FeedEvent event = FeedEvent.postCreated(samplePost(authorId));

        StepVerifier.create(broadcaster.shouldDeliver(event, viewerId, "following"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldDeliver_followingScopeSuppressesNewPostsFromUnfollowedAuthors() {
        when(followRepository.isFollowing(viewerId, authorId)).thenReturn(Mono.just(false));
        FeedEvent event = FeedEvent.postCreated(samplePost(authorId));

        StepVerifier.create(broadcaster.shouldDeliver(event, viewerId, "following"))
                .expectNext(false)
                .verifyComplete();
    }
}
