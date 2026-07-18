package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.ports.ModerationSuggestionPort;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin moderation over the social feed: view/delete any post or comment, bypassing ownership.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialModerationService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final ModerationSuggestionPort moderationSuggestionPort;

    public Flux<PostRepository.FeedRow> listPosts(int limit, int offset) {
        return postRepository.findAllForModeration(limit, offset);
    }

    public Mono<Void> deletePost(String postId) {
        return postRepository.deleteById(UUID.fromString(postId))
                .flatMap(deleted -> deleted
                        ? Mono.<Void>empty().doOnSubscribe(s -> log.info("Admin deleted post id={}", postId))
                        : Mono.error(new NotFoundException("Post not found: " + postId)));
    }

    public Flux<CommentRepository.CommentRow> listComments(String postId, int limit, int offset) {
        return commentRepository.findByPost(UUID.fromString(postId), limit, offset);
    }

    public Mono<Void> deleteComment(String commentId) {
        return commentRepository.deleteById(UUID.fromString(commentId))
                .flatMap(deleted -> deleted
                        ? Mono.<Void>empty().doOnSubscribe(s -> log.info("Admin deleted comment id={}", commentId))
                        : Mono.error(new NotFoundException("Comment not found: " + commentId)));
    }

    public Flux<ReportRepository.ReportRow> listPendingReports(int limit, int offset) {
        return reportRepository.findPending(limit, offset);
    }

    public Mono<Void> dismissReport(String reportId, String adminId) {
        return reportRepository.dismiss(UUID.fromString(reportId), UUID.fromString(adminId))
                .flatMap(dismissed -> dismissed
                        ? Mono.<Void>empty().doOnSubscribe(s -> log.info("Admin {} dismissed report id={}", adminId, reportId))
                        : Mono.error(new NotFoundException("Pending report not found: " + reportId)));
    }

    /**
     * Returns the cached AI moderation suggestion for a report, generating (and persisting) one
     * first if none exists yet or {@code force} is set. Never auto-applies the suggestion — it's
     * advisory only, the admin still picks dismiss/delete explicitly.
     */
    public Mono<ReportRepository.ReportRow> getAiSuggestion(String reportId, boolean force) {
        UUID id = UUID.fromString(reportId);
        return reportRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Report not found: " + reportId)))
                .flatMap(row -> {
                    if (!force && row.aiSuggestedAction() != null) {
                        return Mono.just(row);
                    }
                    String targetType = row.postId() != null ? "POST" : "COMMENT";
                    return moderationSuggestionPort.suggest(targetType, row.targetContent(), row.reason())
                            .flatMap(s -> reportRepository.saveAiSuggestion(id, s.action(), s.reason(), s.confidence())
                                    .then(reportRepository.findById(id)))
                            .doOnSuccess(r -> log.info("Generated AI moderation suggestion for report id={}: {}",
                                    reportId, r.aiSuggestedAction()));
                });
    }
}
