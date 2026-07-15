package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ReportRepository {

    /** @return true if this is a newly-recorded report (false if this user already reported this target). */
    Mono<Boolean> insert(UUID id, UUID reporterId, UUID postId, UUID commentId, String reason);

    /** Pending reports for the admin moderation queue, oldest first. */
    Flux<ReportRow> findPending(int limit, int offset);

    Mono<Long> countPending();

    /** @return true if a PENDING report with this id was dismissed. */
    Mono<Boolean> dismiss(UUID reportId, UUID adminId);

    record ReportRow(
            UUID id,
            UUID reporterId,
            String reporterName,
            UUID postId,
            UUID commentId,
            String targetContent,
            String targetAuthorName,
            String reason,
            String status,
            LocalDateTime createdAt
    ) {
    }
}
