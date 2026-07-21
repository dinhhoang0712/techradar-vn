package com.techpulse.techradar.features.user.domain;

import java.util.UUID;

/**
 * A user resolved from {@code user_profile} (joined with {@code users} for the email) who
 * qualifies for a notification — either because their profile technologies match an alert, or
 * simply because they have at least one technology on file (roadmap-scan candidates) — together
 * with their delivery preferences.
 *
 * <p>Kept in the {@code user} feature (rather than {@code notification}) because {@code
 * user_profile} is this feature's table: notification-specific subscriber queries should not
 * reach into another feature's schema directly. The {@code notification} feature maps this to
 * its own {@code TrendSubscriber} domain type at the boundary.
 */
public record NotificationRecipient(UUID userId, String email, boolean notifyInapp, boolean notifyEmail) {
}
