package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.util.ClientIpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Tag(name = "CompanyInsight", description = "AI-generated company summary endpoints")
@RestController
@RequestMapping("/company-insight")
@RequiredArgsConstructor
public class CompanyInsightController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Generate a short AI narrative about a company's hiring/tech-stack profile")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> insight(
            @RequestBody Map<String, Object> body,
            ServerHttpRequest httpRequest) {
        return requestHandler.forward(
                "/company-insight", body, AiProxyPort.DEFAULT_TIMEOUT,
                "Company insight generated", "Company insight service unavailable",
                ClientIpUtils.resolveClientIp(httpRequest));
    }
}
