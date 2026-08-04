package com.techpulse.techradar.features.auth.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the securityStamp-rotation invariant directly on the entity — the whole point of moving it
 * here from being re-implemented at every call site (ResetPasswordUseCase, AdminUserService,
 * ProfileService). See docs/adr/0008-rich-user-entity-for-security-stamp-invariant.md.
 */
class UserTest {

    private static User user(String role, String status) {
        return User.builder().id(UUID.randomUUID()).email("dev@example.com").role(role).status(status).build();
    }

    @Test
    void changePassword_alwaysRotatesSecurityStamp() {
        User user = user("user", "ACTIVE");
        UUID before = user.getSecurityStamp();

        user.changePassword("new-hash");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getSecurityStamp()).isNotEqualTo(before);
    }

    @Test
    void changeRole_rotatesSecurityStamp_whenRoleActuallyChanges() {
        User user = user("user", "ACTIVE");
        UUID before = user.getSecurityStamp();

        user.changeRole("admin");

        assertThat(user.getRole()).isEqualTo("admin");
        assertThat(user.getSecurityStamp()).isNotEqualTo(before);
    }

    @Test
    void changeRole_doesNotRotateSecurityStamp_whenRoleUnchanged() {
        User user = user("admin", "ACTIVE");
        UUID before = user.getSecurityStamp();

        user.changeRole("admin");

        assertThat(user.getSecurityStamp()).isEqualTo(before);
    }

    @Test
    void changeStatus_rotatesSecurityStamp_whenStatusActuallyChanges() {
        User user = user("user", "ACTIVE");
        UUID before = user.getSecurityStamp();

        user.changeStatus("SUSPENDED");

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        assertThat(user.getSecurityStamp()).isNotEqualTo(before);
    }

    @Test
    void changeStatus_doesNotRotateSecurityStamp_whenStatusUnchanged() {
        User user = user("user", "ACTIVE");
        UUID before = user.getSecurityStamp();

        user.changeStatus("ACTIVE");

        assertThat(user.getSecurityStamp()).isEqualTo(before);
    }

    @Test
    void rotateSecurityStamp_alwaysAssignsAFreshValue() {
        User user = user("user", "ACTIVE");
        user.rotateSecurityStamp();
        UUID first = user.getSecurityStamp();

        user.rotateSecurityStamp();

        assertThat(user.getSecurityStamp()).isNotNull().isNotEqualTo(first);
    }

    @Test
    void ensureSecurityStamp_assignsOne_whenNoneYetExists() {
        User user = user("user", "ACTIVE");
        assertThat(user.getSecurityStamp()).isNull();

        user.ensureSecurityStamp();

        assertThat(user.getSecurityStamp()).isNotNull();
    }

    @Test
    void ensureSecurityStamp_isANoOp_whenAlreadyAssigned() {
        User user = user("user", "ACTIVE");
        user.rotateSecurityStamp();
        UUID existing = user.getSecurityStamp();

        user.ensureSecurityStamp();

        assertThat(user.getSecurityStamp()).isEqualTo(existing);
    }

    @Test
    void isActive_isCaseInsensitive() {
        assertThat(user("user", "ACTIVE").isActive()).isTrue();
        assertThat(user("user", "SUSPENDED").isActive()).isFalse();
    }

    @Test
    void isAdmin_isCaseInsensitive() {
        assertThat(user("ADMIN", "ACTIVE").isAdmin()).isTrue();
        assertThat(user("user", "ACTIVE").isAdmin()).isFalse();
    }
}
