package com.techpulse.techradar.features.messaging.domain;

/** Lightweight peer info embedded in a conversation summary. */
public record UserRef(String id, String fullName, String avatarUrl) {
}
