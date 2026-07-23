package com.techpulse.techradar.shared.security;

import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipGuardTest {

    @Test
    void isOwnerOrPublic_true_whenOwnerIdIsNull() {
        assertThat(OwnershipGuard.isOwnerOrPublic(null, "user-1")).isTrue();
    }

    @Test
    void isOwnerOrPublic_true_whenOwnerIdMatchesCaller() {
        assertThat(OwnershipGuard.isOwnerOrPublic("user-1", "user-1")).isTrue();
    }

    @Test
    void isOwnerOrPublic_false_whenOwnerIdDiffersFromCaller() {
        assertThat(OwnershipGuard.isOwnerOrPublic("user-1", "user-2")).isFalse();
    }

    @Test
    void isOwnerOrPublic_false_whenOwnerIdPresentButCallerIsAnonymous() {
        assertThat(OwnershipGuard.isOwnerOrPublic("user-1", null)).isFalse();
    }

    @Test
    void requireOwner_completesEmpty_whenIdsMatch() {
        StepVerifier.create(OwnershipGuard.requireOwner("user-1", "user-1", "Resource")).verifyComplete();
    }

    @Test
    void requireOwner_failsWithNotFound_whenIdsDiffer() {
        StepVerifier.create(OwnershipGuard.requireOwner("user-1", "user-2", "Resource"))
                .expectErrorMatches(e -> e instanceof NotFoundException && e.getMessage().equals("Resource not found"))
                .verify();
    }

    @Test
    void requireOwner_failsWithNotFound_whenCallerIsAnonymousButResourceHasAnOwner() {
        StepVerifier.create(OwnershipGuard.requireOwner("user-1", null, "Resource"))
                .expectError(NotFoundException.class)
                .verify();
    }
}
