package com.techpulse.techradar.features.messaging.domain;

/** Metadata for a message's optional file/image attachment; the bytes are served separately. */
public record MessageAttachment(
        String contentType,
        String filename,
        int size
) {
}
