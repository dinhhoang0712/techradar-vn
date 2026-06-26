package com.techpulse.techradar.features.auth.ports;

import reactor.core.publisher.Mono;

/**
 * Output port for sending transactional emails.
 */
public interface EmailSender {

    Mono<Void> sendPasswordReset(String toEmail, String token);

    /** Generic transactional email (e.g. notifications). Fire-and-forget at the call site. */
    Mono<Void> sendNotification(String toEmail, String subject, String body);
}
