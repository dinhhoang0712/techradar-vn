package com.techpulse.techradar.features.aiproxy.application;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.features.system.domain.CmsContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.Map;

/**
 * Generates the "official" monthly technology trend report and catalogs it in the admin CMS
 * ({@code cms_content}, type "Report") — before this, every "Report" row in that table was static
 * seed data with no real generator behind it; ReportPage's own {@code GET /report} is public/ad-hoc
 * and was never persisted anywhere. Disabled by default (real LLM cost) — enable with
 * {@code app.report.monthly.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.report.monthly.enabled", havingValue = "true")
public class MonthlyReportSchedulerService {

    private static final int TOP_N = 10;

    private final AiProxyPort aiProxyPort;
    private final CmsService cmsService;

    @Scheduled(cron = "${app.report.monthly.cron:0 0 5 1 * *}")
    public void generateMonthlyReport() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String period = lastMonth.toString(); // e.g. "2026-07" — report_service._parse_period_dates' YYYY-MM branch
        log.info("Monthly report generation starting for period={}", period);

        Map<String, Object> requestBody = Map.of("period", period, "top_n", TOP_N, "format", "markdown");
        aiProxyPort.forward("/report", requestBody, AiProxyPort.DEFAULT_TIMEOUT)
                .flatMap(response -> saveAsCmsReport(lastMonth, response))
                .doOnSuccess(c -> log.info("Monthly report for {} saved to CMS (id={})", period, c != null ? c.getId() : null))
                .onErrorResume(e -> {
                    log.error("Monthly report generation failed for period={}", period, e);
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<CmsContent> saveAsCmsReport(YearMonth month, Map<String, Object> response) {
        Object report = response.get("report");
        if (!(report instanceof String reportText) || reportText.isBlank()) {
            log.warn("Monthly report for {} came back empty — not saving to CMS", month);
            return Mono.empty();
        }
        String title = "Báo cáo xu hướng công nghệ tháng " + month.getMonthValue() + "/" + month.getYear();
        return cmsService.create(title, "Report", month.atDay(1), "Pending", reportText);
    }
}
