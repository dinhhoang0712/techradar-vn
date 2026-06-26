package com.techpulse.techradar.features.user.domain;

/**
 * A stored avatar image (bytes + content type).
 */
public record Avatar(String contentType, byte[] data) {
}
