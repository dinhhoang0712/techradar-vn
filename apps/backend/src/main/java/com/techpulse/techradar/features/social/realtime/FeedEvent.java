package com.techpulse.techradar.features.social.realtime;

import com.techpulse.techradar.features.social.domain.FeedPost;

import java.util.UUID;

/** Wire format for a live feed event, relayed over Redis Pub/Sub and delivered to SSE subscribers. */
public record FeedEvent(
        Type type,
        UUID authorId,
        String postId,
        FeedPost post,
        Long likeCount,
        Long commentCount
) {
    public enum Type {
        POST_CREATED, POST_LIKED, COMMENT_ADDED
    }

    public static FeedEvent postCreated(FeedPost post) {
        return new FeedEvent(Type.POST_CREATED, UUID.fromString(post.author().id()), post.id(), post, null, null);
    }

    public static FeedEvent postLiked(String postId, UUID authorId, long likeCount) {
        return new FeedEvent(Type.POST_LIKED, authorId, postId, null, likeCount, null);
    }

    public static FeedEvent commentAdded(String postId, UUID authorId, long commentCount) {
        return new FeedEvent(Type.COMMENT_ADDED, authorId, postId, null, null, commentCount);
    }
}
