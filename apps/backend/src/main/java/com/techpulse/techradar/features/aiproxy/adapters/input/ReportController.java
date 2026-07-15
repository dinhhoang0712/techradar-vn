package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
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

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Generate a technology trend report for a given period")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> report(
            @RequestParam String period,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "markdown") String format) {
        Map<String, Object> body = Map.of(
                "period", period,
                "top_n", topN,
                "format", format
        );
        return requestHandler.forward(
                "/report", body, AiProxyPort.DEFAULT_TIMEOUT, "Report generated", "Report service unavailable");
    }
}
