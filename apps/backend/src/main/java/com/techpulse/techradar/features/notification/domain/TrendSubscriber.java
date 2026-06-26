package com.techpulse.techradar.features.notification.domain;

import java.util.UUID;

/**
 * A user subscribed to a technology (it is in their {@code user_profile.technologies}),
 * together with their delivery preferences. Resolved when dispatching a trend alert.
 */
public record TrendSubscriber(UUID userId, String email, boolean notifyInapp, boolean notifyEmail) {
}
