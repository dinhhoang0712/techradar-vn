package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Tag(name = "Agent", description = "AI Agent workflow endpoints")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final Duration TIMEOUT = Duration.ofMillis(120_000);

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Run AI agent to answer a complex question using multiple tools")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> agent(
            @RequestBody Map<String, Object> body) {
        return requestHandler.forwardAsCurrentUser(
                "/agent", body, TIMEOUT, "Agent completed", "Agent service unavailable");
    }
}
