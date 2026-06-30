package com.techpulse.techradar.features.summarize.adapters.input;

import com.techpulse.techradar.features.summarize.ports.SummarizeServicePort;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Tag(name = "Summarize", description = "Technology trend summarization endpoints")
@RestController
@RequestMapping("/chat/summarize")
@RequiredArgsConstructor
public class SummarizeController {

    private final SummarizeServicePort summarizeServicePort;

    @Operation(summary = "Summarize technology trend articles for a given period")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> summarize(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body != null ? body : Collections.emptyMap();
        return summarizeServicePort.summarize(request)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Summary generated")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error("Summarize service unavailable", "SERVICE_UNAVAILABLE"))));
    }
}
