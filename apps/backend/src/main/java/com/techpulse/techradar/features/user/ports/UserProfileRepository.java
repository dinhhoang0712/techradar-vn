package com.techpulse.techradar.features.user.ports;

import com.techpulse.techradar.features.user.domain.UserProfile;
import reactor.core.publisher.Mono;

/**
 * Output port for the {@code user_profile} table.
 */
public interface UserProfileRepository {

    Mono<UserProfile> findByUserId(String userId);

    Mono<UserProfile> upsert(UserProfile profile);
}
