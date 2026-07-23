package com.techpulse.techradar.features.user.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR data-portability export: the account, profile, posts and comments belonging to one user.
 * Deliberately scoped to this user's own authored content, not every table their id ever touches
 * (chat/messaging/notifications/follows are not included) - see {@link ExportUserDataUseCase}.
 */
public record UserDataExport(
        Account account,
        Profile profile,
        List<Post> posts,
        List<Comment> comments) {

    public record Account(
            UUID id,
            String email,
            String fullName,
            String role,
            String status,
            String subscriptionTier,
            LocalDateTime createdAt) {
    }

    public record Profile(
            String jobRole,
            String bio,
            String location,
            String avatarUrl,
            List<String> technologies) {
    }

    public record Post(UUID id, String content, LocalDateTime createdAt) {
    }

    public record Comment(UUID id, String content, LocalDateTime createdAt) {
    }
}
