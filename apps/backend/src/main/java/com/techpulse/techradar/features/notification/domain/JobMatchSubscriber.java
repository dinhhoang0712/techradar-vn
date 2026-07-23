package com.techpulse.techradar.features.notification.domain;

import java.util.UUID;

/**
 * Notification-feature view of a job-match subscriber, mapped from
 * {@link com.techpulse.techradar.features.user.domain.JobMatchSubscriber} at the feature
 * boundary — mirrors the existing {@code NotificationRecipient} (user) -&gt; {@code TrendSubscriber}
 * (notification) mapping convention.
 */
public record JobMatchSubscriber(UUID userId, String email, boolean notifyInapp, boolean notifyEmail,
                                  boolean matchesCurrentSkills) {
}
