package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.social.ports.CommentRepository.CommentRow;
import com.techpulse.techradar.features.social.ports.PostRepository.FeedRow;
import com.techpulse.techradar.features.social.ports.ReportRepository.ReportRow;
import com.techpulse.techradar.features.system.adapters.input.AdminSocialDtos.ReportView;
import com.techpulse.techradar.features.system.application.SocialModerationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSocialControllerTest {

    @Mock
    private SocialModerationService moderationService;

    private AdminSocialController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminSocialController(moderationService);
    }

    private static FeedRow feedRow(UUID id) {
        return new FeedRow(id, UUID.randomUUID(), "Author", "avatar.png", "content", LocalDateTime.now(),
                5, 2, false, List.of(), List.of(), null, null, null);
    }

    @Test
    void listPosts_returnsPostsFromModerationService() {
        UUID postId = UUID.randomUUID();
        when(moderationService.listPosts(20, 0)).thenReturn(Flux.just(feedRow(postId)));

        StepVerifier.create(controller.listPosts(0, 20))
                .assertNext(response -> {
                    assertThat(response.getBody().getData()).hasSize(1);
                    assertThat(response.getBody().getData().get(0).getId()).isEqualTo(postId.toString());
                })
                .verifyComplete();
    }

    @Test
    void listPosts_clampsOversizedPageSize_toMaxSize() {
        // Regression test for a bug where PageRequest.of(page, size, size, size) made the
        // "max size" clamp a no-op (Math.min(size, size) == size) - a client requesting an
        // enormous page got exactly that instead of being capped.
        when(moderationService.listPosts(100, 0)).thenReturn(Flux.empty());

        StepVerifier.create(controller.listPosts(0, 999_999))
                .assertNext(response -> assertThat(response.getBody().getData()).isEmpty())
                .verifyComplete();

        verify(moderationService).listPosts(100, 0);
    }

    @Test
    void listPosts_fallsBackToDefaultSize_whenSizeIsZeroOrNegative() {
        // Same bug, other edge: with defaultSize == size, a size of 0 (or negative) "fell back"
        // to itself instead of a sane positive default, producing an empty LIMIT 0 page - or,
        // for negative size, a negative LIMIT/OFFSET the database would reject outright.
        when(moderationService.listPosts(20, 0)).thenReturn(Flux.empty());

        StepVerifier.create(controller.listPosts(0, 0))
                .assertNext(response -> assertThat(response.getBody().getData()).isEmpty())
                .verifyComplete();

        verify(moderationService).listPosts(20, 0);
    }

    @Test
    void deletePost_delegatesToModerationService() {
        UUID postId = UUID.randomUUID();
        when(moderationService.deletePost(postId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(controller.deletePost(postId.toString()))
                .assertNext(response -> assertThat(response.getBody().isSuccess()).isTrue())
                .verifyComplete();

        verify(moderationService).deletePost(postId.toString());
    }

    @Test
    void listComments_returnsCommentsFromModerationService() {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        CommentRow row = new CommentRow(commentId, UUID.randomUUID(), "Author", "avatar.png", "comment text",
                null, LocalDateTime.now());
        when(moderationService.listComments(postId.toString(), 20, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(controller.listComments(postId.toString(), 0, 20))
                .assertNext(response -> {
                    assertThat(response.getBody().getData()).hasSize(1);
                    assertThat(response.getBody().getData().get(0).getId()).isEqualTo(commentId.toString());
                })
                .verifyComplete();
    }

    @Test
    void deleteComment_delegatesToModerationService() {
        UUID commentId = UUID.randomUUID();
        when(moderationService.deleteComment(commentId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteComment(commentId.toString()))
                .assertNext(response -> assertThat(response.getBody().isSuccess()).isTrue())
                .verifyComplete();

        verify(moderationService).deleteComment(commentId.toString());
    }

    @Test
    void listReports_returnsPendingReportsFromModerationService() {
        UUID reportId = UUID.randomUUID();
        ReportRow row = new ReportRow(reportId, UUID.randomUUID(), "Reporter", UUID.randomUUID(), null,
                "content", "Author", "spam", "PENDING", LocalDateTime.now(), null, null, null, null);
        when(moderationService.listPendingReports(20, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(controller.listReports(0, 20))
                .assertNext(response -> assertThat(response.getBody().getData()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void dismissReport_delegatesToModerationServiceWithCurrentAdminId() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(moderationService.dismissReport(reportId.toString(), adminId.toString())).thenReturn(Mono.empty());
        var authentication = new TestingAuthenticationToken(adminId.toString(), null, List.of());

        StepVerifier.create(controller.dismissReport(reportId.toString())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .assertNext(response -> assertThat(response.getBody().isSuccess()).isTrue())
                .verifyComplete();

        verify(moderationService).dismissReport(reportId.toString(), adminId.toString());
    }

    @Test
    void getAiSuggestion_returnsSuggestionInReportView() {
        UUID reportId = UUID.randomUUID();
        ReportRow row = new ReportRow(
                reportId, UUID.randomUUID(), "Reporter",
                UUID.randomUUID(), null,
                "nội dung bị báo cáo", "Author",
                "spam", "PENDING", LocalDateTime.now(),
                "REMOVE", "vi phạm chính sách", 0.85, LocalDateTime.now()
        );
        when(moderationService.getAiSuggestion(reportId.toString(), false)).thenReturn(Mono.just(row));

        StepVerifier.create(controller.getAiSuggestion(reportId.toString(), false))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<ReportView> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData().getAiSuggestedAction()).isEqualTo("REMOVE");
                    assertThat(body.getData().getAiConfidence()).isEqualTo(0.85);
                    assertThat(body.getData().getTargetType()).isEqualTo("POST");
                })
                .verifyComplete();
    }

    @Test
    void getAiSuggestion_forwardsForceFlagToService() {
        UUID reportId = UUID.randomUUID();
        ReportRow row = new ReportRow(
                reportId, UUID.randomUUID(), "Reporter",
                null, UUID.randomUUID(),
                "bình luận vi phạm", "Author",
                "toxic", "PENDING", LocalDateTime.now(),
                "DISMISS", "không vi phạm", 0.4, LocalDateTime.now()
        );
        when(moderationService.getAiSuggestion(reportId.toString(), true)).thenReturn(Mono.just(row));

        StepVerifier.create(controller.getAiSuggestion(reportId.toString(), true))
                .assertNext(response -> assertThat(response.getBody().getData().getTargetType()).isEqualTo("COMMENT"))
                .verifyComplete();

        verify(moderationService).getAiSuggestion(reportId.toString(), true);
    }
}
