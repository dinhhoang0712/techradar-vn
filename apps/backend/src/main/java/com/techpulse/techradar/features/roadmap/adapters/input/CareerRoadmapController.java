package com.techpulse.techradar.features.roadmap.adapters.input;

import com.techpulse.techradar.features.roadmap.application.GetCareerRoadmapUseCase;
import com.techpulse.techradar.features.roadmap.application.SimulateCareerMoveUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Career", description = "Unified career roadmap: recommended skills, role roadmap and matching jobs")
@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
public class CareerRoadmapController {

    private final GetCareerRoadmapUseCase getCareerRoadmapUseCase;
    private final SimulateCareerMoveUseCase simulateCareerMoveUseCase;

    @Operation(
            summary = "Personalized career roadmap for the current user",
            description = "Combines /recommend (next skills), /career (role roadmap) and /jobs/matches " +
                          "(matching jobs) into one cached call. Returns has_technologies=false (with " +
                          "empty sections) rather than an error when the profile has no technologies yet."
    )
    @GetMapping("/roadmap")
    public Mono<ResponseEntity<ApiResponse<RoadmapDtos.RoadmapResponse>>> roadmap() {
        return SecurityUtils.currentUserId()
                .flatMap(getCareerRoadmapUseCase::execute)
                .map(RoadmapDtos.RoadmapResponse::from)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Roadmap generated")));
    }

    @Operation(
            summary = "What-if: simulate learning a hypothetical technology",
            description = "Previews the effect of adding one technology to the current user's skill set " +
                          "(without persisting it): job-match count before/after, real market salary stats, " +
                          "and a statistical+LLM trend forecast — combining /jobs/matches scoring, " +
                          "/salary/tech and /forecast."
    )
    @GetMapping("/simulate")
    public Mono<ResponseEntity<ApiResponse<SimulationDtos.SimulationResponse>>> simulate(
            @RequestParam String technology) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> simulateCareerMoveUseCase.execute(userId, technology))
                .map(SimulationDtos.SimulationResponse::from)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Simulation computed")));
    }
}
