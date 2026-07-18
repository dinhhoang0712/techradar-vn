package com.techpulse.techradar.features.system.ports;

import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Persistence port for the append-only audit trail.
 */
public interface AuditLogRepository {

    Mono<Void> insert(AuditLogEntry entry);

    Flux<AuditLogEntry> list(int limit, int offset);
}
