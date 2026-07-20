package com.techpulse.techradar.features.user.domain;

import java.util.List;

/**
 * Small helpers for reading derived values off {@link UserProfile} consistently, so that every
 * caller agrees on what an "empty"/missing value means (e.g. a {@code null} technologies list).
 */
public final class UserProfiles {

    private UserProfiles() {
    }

    /**
     * The profile's technology list, or an empty list if the profile has none set.
     */
    public static List<String> technologiesOrEmpty(UserProfile profile) {
        List<String> technologies = profile.getTechnologies();
        return technologies == null ? List.of() : technologies;
    }
}
