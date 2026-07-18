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

    /** @return the report row (any status), or empty if no report with this id exists. */
    Mono<ReportRow> findById(UUID reportId);

    /** @return true if a PENDING report with this id was dismissed. */
    Mono<Boolean> dismiss(UUID reportId, UUID adminId);

    /** @return true if the AI moderation suggestion was persisted for this report. */
    Mono<Boolean> saveAiSuggestion(UUID reportId, String action, String reason, double confidence);

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
            LocalDateTime createdAt,
            String aiSuggestedAction,
            String aiSuggestedReason,
            Double aiConfidence,
            LocalDateTime aiSuggestedAt
    ) {
    }
}
