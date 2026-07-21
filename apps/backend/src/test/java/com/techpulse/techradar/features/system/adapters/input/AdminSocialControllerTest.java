package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.social.ports.ReportRepository.ReportRow;
import com.techpulse.techradar.features.system.adapters.input.AdminSocialDtos.ReportView;
import com.techpulse.techradar.features.system.application.SocialModerationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
