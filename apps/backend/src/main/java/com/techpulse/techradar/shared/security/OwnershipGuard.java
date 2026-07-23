package com.techpulse.techradar.shared.security;

import com.techpulse.techradar.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Reusable ownership checks for flows that have already loaded a resource and need to compare its
 * owner id against the caller (e.g. chat sessions). Mutations that can push the ownership check
 * into the query itself instead - e.g. DeletePostUseCase.deleteOwnedBy, NotificationService's
 * userId-scoped updates - should keep doing that: it is atomic and avoids the load-then-check race
 * this class does not protect against.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    /** @return true if {@code ownerId} is absent (public/anonymous resource) or matches {@code currentUserId}. */
    public static boolean isOwnerOrPublic(String ownerId, String currentUserId) {
        return ownerId == null || ownerId.equals(currentUserId);
    }

    /**
     * @return empty when {@code ownerId} matches {@code currentUserId}; a {@link NotFoundException}
     * otherwise, deliberately 404 rather than 403 so the check doesn't leak whether the resource exists.
     */
    public static Mono<Void> requireOwner(String ownerId, String currentUserId, String resourceName) {
        return Objects.equals(ownerId, currentUserId)
                ? Mono.empty()
                : Mono.error(new NotFoundException(resourceName + " not found"));
    }
}
