package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.application.GetPublicStatsUseCase;
import com.techpulse.techradar.features.system.domain.PublicStats;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public (no-auth) real aggregate counts, shown as decorative stat chips on the login/register pages.
 */
@Tag(name = "Public Stats", description = "Public real aggregate counts (companies/jobs/users)")
@RestController
@RequestMapping("/stats/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private final GetPublicStatsUseCase getPublicStatsUseCase;

    @Operation(summary = "Real companies/jobs/users counts for public marketing chips")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<PublicStats>>> stats() {
        return getPublicStatsUseCase.execute()
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)));
    }
}
