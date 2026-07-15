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

@Tag(name = "Interview", description = "AI mock interview endpoints")
@RestController
@RequestMapping("/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Run one turn of a mock interview (empty history starts a new session)")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> interview(
            @RequestBody Map<String, Object> body) {
        return requestHandler.forwardAsCurrentUser(
                "/interview", body, AiProxyPort.DEFAULT_TIMEOUT, "Interview turn generated", "Interview service unavailable");
    }
}
