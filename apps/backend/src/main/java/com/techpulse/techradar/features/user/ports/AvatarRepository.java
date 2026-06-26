package com.techpulse.techradar.features.user.ports;

import com.techpulse.techradar.features.user.domain.Avatar;
import reactor.core.publisher.Mono;

/**
 * Output port for avatar image storage ({@code user_avatar} table).
 */
public interface AvatarRepository {

    Mono<Void> save(String userId, String contentType, byte[] data);

    Mono<Avatar> find(String userId);
}
