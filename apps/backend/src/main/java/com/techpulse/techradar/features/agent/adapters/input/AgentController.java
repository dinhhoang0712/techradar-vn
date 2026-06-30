package com.techpulse.techradar.features.agent.adapters.input;

import com.techpulse.techradar.features.agent.ports.AgentServicePort;
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

@Tag(name = "Agent", description = "AI Agent workflow endpoints")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentServicePort agentServicePort;

    @Operation(summary = "Run AI agent to answer a complex question using multiple tools")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> agent(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> base = body != null ? body : Collections.emptyMap();
        return SecurityUtils.currentUserId()
                .map(userId -> {
                    Map<String, Object> request = new HashMap<>(base);
                    request.put("user_id", userId);
                    return request;
                })
                .defaultIfEmpty(base)
                .flatMap(agentServicePort::runAgent)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Agent completed")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Agent service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
