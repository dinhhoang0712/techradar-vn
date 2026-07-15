package com.techpulse.techradar.features.social.domain;

/** Lightweight author info embedded in a Post/Comment. */
public record UserSummary(String id, String fullName, String avatarUrl) {
}
