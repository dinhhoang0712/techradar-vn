package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import com.techpulse.techradar.features.system.ports.AuditLogRepository;
import com.techpulse.techradar.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Records admin mutations for governance ("who did what, when"). Best-effort: a failure to write
 * the audit row (e.g. a transient DB hiccup) is logged and swallowed rather than propagated, so
 * the audit trail — a secondary concern — never fails the admin action that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * Actor is resolved from the current reactive security context (the authenticated admin).
     * Chain with {@code .then(...)} / {@code .flatMap(x -> record(...).thenReturn(x))} after the
     * mutation succeeds — never before, so a failed mutation never gets logged as if it happened.
     */
    public Mono<Void> record(String action, String targetType, String targetId, String details) {
        return SecurityUtils.currentUserId()
                .flatMap(actorId -> repository.insert(AuditLogEntry.builder()
                        .actorId(UUID.fromString(actorId))
                        .action(action)
                        .targetType(targetType)
                        .targetId(targetId)
                        .details(details)
                        .build()))
                .onErrorResume(e -> {
                    log.warn("Failed to record audit log entry action={} targetType={} targetId={}",
                            action, targetType, targetId, e);
                    return Mono.empty();
                });
    }

    public Flux<AuditLogEntry> list(int limit, int offset) {
        return repository.list(limit, offset);
    }
}
