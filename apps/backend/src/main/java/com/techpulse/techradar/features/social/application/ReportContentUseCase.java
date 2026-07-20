package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * User-facing "report" (flag) on a post or comment, reviewed later in the admin moderation queue.
 */
@Component
@RequiredArgsConstructor
public class ReportContentUseCase {

    private static final int MAX_REASON_LENGTH = 500;

    private final ReportRepository reportRepository;

    public Mono<Void> reportPost(String postId, String reporterId, String reason) {
        return execute(UUID.fromString(postId), null, reporterId, reason);
    }

    public Mono<Void> reportComment(String commentId, String reporterId, String reason) {
        return execute(null, UUID.fromString(commentId), reporterId, reason);
    }

    private Mono<Void> execute(UUID postId, UUID commentId, String reporterId, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_REASON, "Report reason must not be empty"));
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_REASON, "Report reason too long (max " + MAX_REASON_LENGTH + " chars)"));
        }
        return reportRepository.insert(UUID.randomUUID(), UUID.fromString(reporterId), postId, commentId, trimmed)
                .then();
    }
}
