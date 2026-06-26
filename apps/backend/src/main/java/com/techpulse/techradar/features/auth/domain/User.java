package com.techpulse.techradar.features.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User domain entity.
 * No Spring dependencies - pure domain model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String passwordHash;
    private String fullName;
    private String role;
    private String status;
    private String subscriptionTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
