package com.techpulse.techradar.features.user.domain;

import java.util.UUID;

/**
 * A user resolved from {@code user_profile} who qualifies for a job-match notification — either
 * because the job's technologies overlap their current {@code technologies}, or their
 * {@code target_skills} (the roadmap's "learning next" recommendations), or both — together with
 * their delivery preferences. {@code matchesCurrentSkills} distinguishes the two so the
 * notification feature can send different copy for each.
 */
public record JobMatchSubscriber(UUID userId, String email, boolean notifyInapp, boolean notifyEmail,
                                  boolean matchesCurrentSkills) {
}
