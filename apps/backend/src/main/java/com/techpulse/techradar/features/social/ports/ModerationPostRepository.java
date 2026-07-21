package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin moderation over posts — split out of {@link PostRepository} so the feed-facing port isn't
 * also the moderation port; the two have unrelated callers ({@code SocialModerationService} vs.
 * the social feature's own use cases) and unrelated change reasons.
 */
public interface ModerationPostRepository {

    /** Every post regardless of author/followers, newest first. */
    Flux<PostRepository.FeedRow> findAllForModeration(int limit, int offset);

    /** Delete any post by id, bypassing ownership. @return true if it existed. */
    Mono<Boolean> deleteById(UUID postId);
}
