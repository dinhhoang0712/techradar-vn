package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.user.domain.UserProfile;

/**
 * The account ({@link User}) together with its extended {@link UserProfile}.
 */
public record ProfileData(User user, UserProfile profile) {
}
