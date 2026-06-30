package com.techpulse.techradar.features.report.adapters.input;

import com.techpulse.techradar.features.report.ports.ReportServicePort;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Tag(name = "Report", description = "Technology trend report endpoints")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportServicePort reportServicePort;

    @Operation(summary = "Generate a technology trend report for a given period")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> report(
            @RequestParam String period,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "markdown") String format) {
        return reportServicePort.generateReport(period, topN, format)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Report generated")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Report service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
