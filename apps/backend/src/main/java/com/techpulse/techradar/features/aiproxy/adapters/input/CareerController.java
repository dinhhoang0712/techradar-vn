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

@Tag(name = "Career", description = "Career path recommendation endpoints")
@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
public class CareerController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Get career roadmap toward a target role")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> career(
            @RequestBody Map<String, Object> body) {
        return requestHandler.forwardAsCurrentUser(
                "/career", body, AiProxyPort.DEFAULT_TIMEOUT, "Career advice generated", "Career service unavailable");
    }
}
