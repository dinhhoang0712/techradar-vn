package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

public class HealthController {

    @Tag(name = "Health", description = "Health check endpoints")
    @Operation(summary = "Check API health status")
    @GetMapping("/health")
    public Mono<ResponseEntity<ApiResponse<HealthStatus>>> health() {
        HealthStatus status = HealthStatus.builder()
                .status("UP")
                .version("2.0.0")
                .timestamp(System.currentTimeMillis())
                .build();

        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(status, "Service is healthy")
        ));
    }
}
