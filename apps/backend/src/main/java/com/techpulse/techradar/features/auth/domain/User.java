package com.techpulse.techradar.features.auth.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User domain entity. No Spring dependencies - pure domain model.
 * <p>
 * {@code passwordHash}/{@code role}/{@code status}/{@code securityStamp} have NO public setter on
 * purpose. Before this, every call site that changed one of these fields (ResetPasswordUseCase,
 * AdminUserService, ProfileService) re-implemented "also rotate securityStamp" by hand via the
 * plain Lombok setter — nothing stopped a future caller from mutating role/status/password while
 * forgetting the paired rotation, which is a live security bug (the change takes effect but the
 * old JWT stays valid). The invariant now lives here instead, see
 * {@code docs/adr/0008-rich-user-entity-for-security-stamp-invariant.md}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class User {
    private UUID id;
    private String email;
    @Setter(AccessLevel.NONE)
    private String passwordHash;
    private String fullName;
    @Setter(AccessLevel.NONE)
    private String role;
    @Setter(AccessLevel.NONE)
    private String status;
    private String subscriptionTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Bumped whenever role/status/password changes, so already-issued access tokens can be invalidated early. */
    @Setter(AccessLevel.NONE)
    private UUID securityStamp;

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    /** Always rotates {@link #securityStamp} — a password change must revoke every other session. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        rotateSecurityStamp();
    }

    /** No-op (no stamp rotation) if {@code newRole} equals the current role. */
    public void changeRole(String newRole) {
        if (newRole.equals(this.role)) {
            return;
        }
        this.role = newRole;
        rotateSecurityStamp();
    }

    /** No-op (no stamp rotation) if {@code newStatus} equals the current status. */
    public void changeStatus(String newStatus) {
        if (newStatus.equals(this.status)) {
            return;
        }
        this.status = newStatus;
        rotateSecurityStamp();
    }

    /**
     * Escape hatch for policies that force re-auth elsewhere but aren't a universal invariant of
     * this entity — e.g. {@code ProfileService} rotates on a self-service email change (changing a
     * common account-recovery identifier), which plain email assignment doesn't imply on its own
     * (an admin changing a user's email via {@code AdminUserService} does NOT rotate the stamp —
     * see the ADR for why the two flows differ here).
     */
    public void rotateSecurityStamp() {
        this.securityStamp = UUID.randomUUID();
    }

    /** First-time stamp assignment for a brand-new user with none yet (repository, on insert). */
    public void ensureSecurityStamp() {
        if (this.securityStamp == null) {
            rotateSecurityStamp();
        }
    }
}
