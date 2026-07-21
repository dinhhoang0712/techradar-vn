package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.ModerationPostRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository.ReportRow;
import com.techpulse.techradar.features.system.ports.ModerationSuggestionPort;
import com.techpulse.techradar.features.system.ports.ModerationSuggestionPort.Suggestion;
import com.techpulse.techradar.shared.exception.NotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialModerationServiceTest {

    @Mock
    private ModerationPostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ModerationSuggestionPort moderationSuggestionPort;

    private SocialModerationService service;

    private static final UUID REPORT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SocialModerationService(postRepository, commentRepository, reportRepository, moderationSuggestionPort);
    }

    private static ReportRow row(String aiAction, String aiReason, Double aiConfidence) {
        return new ReportRow(
                REPORT_ID, UUID.randomUUID(), "Reporter",
                UUID.randomUUID(), null,
                "nội dung bị báo cáo", "Author",
                "spam", "PENDING", LocalDateTime.now(),
                aiAction, aiReason, aiConfidence, aiAction != null ? LocalDateTime.now() : null
        );
    }

    @Test
    void getAiSuggestion_returnsCached_whenPresentAndNotForced() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Mono.just(row("REMOVE", "đã có gợi ý", 0.9)));

        StepVerifier.create(service.getAiSuggestion(REPORT_ID.toString(), false))
                .assertNext(r -> assertThat(r.aiSuggestedAction()).isEqualTo("REMOVE"))
                .verifyComplete();

        verify(moderationSuggestionPort, never()).suggest(anyString(), anyString(), anyString());
        verify(reportRepository, never()).saveAiSuggestion(any(), anyString(), anyString(), anyDouble());
    }

    @Test
    void getAiSuggestion_generatesAndPersists_whenNoneCached() {
        ReportRow initial = row(null, null, null);
        ReportRow updated = row("DISMISS", "không vi phạm", 0.6);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Mono.just(initial), Mono.just(updated));
        when(moderationSuggestionPort.suggest("POST", "nội dung bị báo cáo", "spam"))
                .thenReturn(Mono.just(new Suggestion("DISMISS", "không vi phạm", 0.6)));
        when(reportRepository.saveAiSuggestion(REPORT_ID, "DISMISS", "không vi phạm", 0.6))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.getAiSuggestion(REPORT_ID.toString(), false))
                .assertNext(r -> assertThat(r.aiSuggestedAction()).isEqualTo("DISMISS"))
                .verifyComplete();

        verify(reportRepository).saveAiSuggestion(REPORT_ID, "DISMISS", "không vi phạm", 0.6);
    }

    @Test
    void getAiSuggestion_forceRegenerates_evenWhenCached() {
        ReportRow cached = row("REMOVE", "cũ", 0.5);
        ReportRow refreshed = row("DISMISS", "mới", 0.8);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Mono.just(cached), Mono.just(refreshed));
        when(moderationSuggestionPort.suggest(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new Suggestion("DISMISS", "mới", 0.8)));
        when(reportRepository.saveAiSuggestion(any(), anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.getAiSuggestion(REPORT_ID.toString(), true))
                .assertNext(r -> assertThat(r.aiSuggestedAction()).isEqualTo("DISMISS"))
                .verifyComplete();

        verify(moderationSuggestionPort).suggest(anyString(), anyString(), anyString());
    }

    @Test
    void getAiSuggestion_errorsNotFound_whenReportMissing() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getAiSuggestion(REPORT_ID.toString(), false))
                .expectError(NotFoundException.class)
                .verify();
    }
}
