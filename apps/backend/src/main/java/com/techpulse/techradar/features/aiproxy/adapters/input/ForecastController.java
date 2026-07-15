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

@Tag(name = "Forecast", description = "Technology trend forecast endpoints")
@RestController
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Forecast technology trend for a given horizon")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forecast(
            @RequestParam String technology,
            @RequestParam(defaultValue = "6") int horizonMonths) {
        Map<String, Object> body = Map.of(
                "technology", technology,
                "horizon_months", horizonMonths
        );
        return requestHandler.forward(
                "/forecast", body, AiProxyPort.DEFAULT_TIMEOUT, "Forecast generated", "Forecast service unavailable");
    }
}
