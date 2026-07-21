package com.techpulse.techradar.features.social.domain;

/**
 * Feed visibility scope, as accepted by {@code GET /feed} and {@code GET /feed/stream}'s
 * {@code scope} query param: {@link #FOLLOWING} (self + followees, the default) or
 * {@link #EXPLORE} (every public post).
 */
public enum FeedScope {
    FOLLOWING,
    EXPLORE;

    /**
     * Parses the raw {@code scope} query param. Anything other than (case-insensitive)
     * {@code "explore"} — including {@code null}, blank, or an unrecognized value — resolves to
     * {@link #FOLLOWING}, matching the pre-existing "default following" behavior at both call
     * sites this replaces.
     */
    public static FeedScope fromParam(String raw) {
        return "explore".equalsIgnoreCase(raw) ? EXPLORE : FOLLOWING;
    }
}
