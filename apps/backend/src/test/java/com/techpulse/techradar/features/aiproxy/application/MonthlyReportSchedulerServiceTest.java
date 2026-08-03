package com.techpulse.techradar.features.aiproxy.application;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.features.system.domain.CmsContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportSchedulerServiceTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private CmsService cmsService;

    private MonthlyReportSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonthlyReportSchedulerService(aiProxyPort, cmsService);
    }

    private static Map<String, Object> reportResponse(String report) {
        Map<String, Object> body = new HashMap<>();
        body.put("period", "2026-07");
        body.put("report", report);
        body.put("top_techs", java.util.List.of());
        body.put("generated_at", "2026-08-01 05:00 UTC");
        return body;
    }

    @Test
    void generateMonthlyReport_requestsLastMonthAndSavesReportBodyToCms() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        when(aiProxyPort.forward(eq("/report"), any(), eq(AiProxyPort.DEFAULT_TIMEOUT)))
                .thenReturn(Mono.just(reportResponse("# Báo cáo\n\nNội dung tháng trước")));
        when(cmsService.create(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(CmsContent.builder().build()));

        scheduler.generateMonthlyReport();

        verify(aiProxyPort).forward(
                eq("/report"),
                eq(Map.of("period", lastMonth.toString(), "top_n", 10, "format", "markdown")),
                eq(AiProxyPort.DEFAULT_TIMEOUT));
        verify(cmsService).create(
                eq("Báo cáo xu hướng công nghệ tháng " + lastMonth.getMonthValue() + "/" + lastMonth.getYear()),
                eq("Report"),
                eq(lastMonth.atDay(1)),
                eq("Pending"),
                eq("# Báo cáo\n\nNội dung tháng trước"));
    }

    @Test
    void generateMonthlyReport_doesNotSave_whenReportBodyIsBlank() {
        when(aiProxyPort.forward(eq("/report"), any(), eq(AiProxyPort.DEFAULT_TIMEOUT)))
                .thenReturn(Mono.just(reportResponse("   ")));

        scheduler.generateMonthlyReport();

        verify(cmsService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void generateMonthlyReport_doesNotThrow_whenAiProxyFails() {
        when(aiProxyPort.forward(eq("/report"), any(), eq(AiProxyPort.DEFAULT_TIMEOUT)))
                .thenReturn(Mono.error(new RuntimeException("ai-rag-core unavailable")));

        scheduler.generateMonthlyReport();

        verify(cmsService, never()).create(any(), any(), any(), any(), any());
    }
}
