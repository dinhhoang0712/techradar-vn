package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportContentUseCaseTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportContentUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ReportContentUseCase(reportRepository);
    }

    @Test
    void reportPost_insertsWithTheCommentIdLeftNull() {
        when(reportRepository.insert(any(), eq(reporterId), eq(postId), isNull(), eq("Spam")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), "Spam")).verifyComplete();

        verify(reportRepository).insert(any(), eq(reporterId), eq(postId), isNull(), eq("Spam"));
    }

    @Test
    void reportComment_insertsWithThePostIdLeftNull() {
        when(reportRepository.insert(any(), eq(reporterId), isNull(), eq(commentId), eq("Abusive")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.reportComment(commentId.toString(), reporterId.toString(), "Abusive")).verifyComplete();

        verify(reportRepository).insert(any(), eq(reporterId), isNull(), eq(commentId), eq("Abusive"));
    }

    @Test
    void reportPost_trimsTheReasonBeforePersisting() {
        when(reportRepository.insert(any(), any(), any(), any(), eq("Spam")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), "  Spam  ")).verifyComplete();

        verify(reportRepository).insert(any(), eq(reporterId), eq(postId), isNull(), eq("Spam"));
    }

    @Test
    void reportPost_completesTheSameWayWhetherOrNotTheRepositorySaysItWasNew() {
        // insert() distinguishes a fresh report (true) from a repeat report by the same user on the
        // same target (false) purely for repo-level bookkeeping; the use case itself is idempotent
        // to the caller and completes successfully either way instead of surfacing an error.
        when(reportRepository.insert(any(), eq(reporterId), eq(postId), isNull(), eq("Spam")))
                .thenReturn(Mono.just(true))
                .thenReturn(Mono.just(false));

        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), "Spam")).verifyComplete();
        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), "Spam")).verifyComplete();

        verify(reportRepository, times(2)).insert(any(), eq(reporterId), eq(postId), isNull(), eq("Spam"));
    }

    @Test
    void reportPost_rejectsANullReasonWithoutCallingTheRepository() {
        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_REASON.name());
                })
                .verify();

        verify(reportRepository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void reportPost_rejectsABlankReasonWithoutCallingTheRepository() {
        StepVerifier.create(useCase.reportPost(postId.toString(), reporterId.toString(), "   "))
                .expectError(BadRequestException.class)
                .verify();

        verify(reportRepository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void reportComment_rejectsAReasonLongerThanTheMax() {
        String tooLong = "a".repeat(501);

        StepVerifier.create(useCase.reportComment(commentId.toString(), reporterId.toString(), tooLong))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_REASON.name());
                })
                .verify();

        verify(reportRepository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void reportComment_acceptsAReasonExactlyAtTheMax() {
        String maxLength = "a".repeat(500);
        when(reportRepository.insert(any(), eq(reporterId), isNull(), eq(commentId), eq(maxLength)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.reportComment(commentId.toString(), reporterId.toString(), maxLength)).verifyComplete();
    }
}
