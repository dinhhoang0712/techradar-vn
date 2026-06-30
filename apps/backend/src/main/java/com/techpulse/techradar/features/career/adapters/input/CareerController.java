package com.techpulse.techradar.features.career.adapters.input;

import com.techpulse.techradar.features.career.ports.CareerServicePort;
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

@Tag(name = "Career", description = "Career path recommendation endpoints")
@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
public class CareerController {

    private final CareerServicePort careerServicePort;

    @Operation(summary = "Get career roadmap toward a target role")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> career(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> base = body != null ? body : Collections.emptyMap();
        return SecurityUtils.currentUserId()
                .map(userId -> {
                    Map<String, Object> request = new HashMap<>(base);
                    request.put("user_id", userId);
                    return request;
                })
                .defaultIfEmpty(base)
                .flatMap(careerServicePort::getCareerAdvice)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Career advice generated")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Career service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
