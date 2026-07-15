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

@Tag(name = "Recommend", description = "Technology recommendation endpoints")
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final AiProxyRequestHandler requestHandler;

    @Operation(summary = "Get technology recommendations for the authenticated user")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> recommend(
            @RequestBody(required = false) Map<String, Object> body) {
        return requestHandler.forwardAsCurrentUser(
                "/recommend", body, AiProxyPort.DEFAULT_TIMEOUT, "Recommendations generated", "Recommendation service unavailable");
    }
}
