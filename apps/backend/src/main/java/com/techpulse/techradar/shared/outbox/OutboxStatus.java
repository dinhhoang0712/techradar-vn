package com.techpulse.techradar.shared.outbox;

/** Lifecycle of one {@code outbox_event} row — see ADR-0005. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
