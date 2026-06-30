package com.techpulse.techradar.features.forecast.adapters.input;

import com.techpulse.techradar.features.forecast.ports.ForecastServicePort;
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

    private final ForecastServicePort forecastServicePort;

    @Operation(summary = "Forecast technology trend for a given horizon")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forecast(
            @RequestParam String technology,
            @RequestParam(defaultValue = "6") int horizonMonths) {
        return forecastServicePort.getForecast(technology, horizonMonths)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Forecast generated")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Forecast service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
