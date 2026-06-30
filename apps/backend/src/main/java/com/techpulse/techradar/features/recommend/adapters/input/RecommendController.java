package com.techpulse.techradar.features.recommend.adapters.input;

import com.techpulse.techradar.features.recommend.ports.RecommendServicePort;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Recommend", description = "Technology recommendation endpoints")
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendServicePort recommendServicePort;

    @Operation(summary = "Get technology recommendations for the authenticated user")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> recommend(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> base = body != null ? body : Collections.emptyMap();
        return SecurityUtils.currentUserId()
                .map(userId -> {
                    Map<String, Object> request = new HashMap<>(base);
                    request.put("user_id", userId);
                    return request;
                })
                .defaultIfEmpty(base)
                .flatMap(recommendServicePort::getRecommendations)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Recommendations generated")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Recommendation service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
