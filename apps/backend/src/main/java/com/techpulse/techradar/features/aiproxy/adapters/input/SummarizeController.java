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

@Tag(name = "Summarize", description = "Technology trend summarization endpoints")
@RestController
@RequestMapping("/chat/summarize")
@RequiredArgsConstructor
public class SummarizeController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Summarize technology trend articles for a given period")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> summarize(
            @RequestBody(required = false) Map<String, Object> body,
            ServerHttpRequest httpRequest) {
        return requestHandler.forward(
                "/summarize", body, AiProxyPort.DEFAULT_TIMEOUT, "Summary generated", "Summarize service unavailable",
                ClientIpUtils.resolveClientIp(httpRequest));
    }
}
