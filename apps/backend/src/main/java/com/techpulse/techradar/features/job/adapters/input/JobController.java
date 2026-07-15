package com.techpulse.techradar.features.job.adapters.input;

import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
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

import java.util.List;

/**
 * Job Matching API — job postings ranked by skill overlap with the caller's profile.
 */
@Tag(name = "Job Matching", description = "Job postings ranked by skill overlap with the user's profile")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final GetJobMatchesUseCase getJobMatchesUseCase;

    @Operation(
            summary = "Jobs matching the current user's profile skills",
            description = "Ranks Job postings by the fraction of required skills present in the caller's " +
                          "profile technologies. Requires authentication; returns an empty list if the " +
                          "profile has no technologies set."
    )
    @GetMapping("/matches")
    public Mono<ResponseEntity<ApiResponse<List<JobDtos.JobMatchResponse>>>> matches(
            @RequestParam(required = false) String location,
            @RequestParam(name = "min_salary", required = false) Double minSalary,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> getJobMatchesUseCase.execute(userId, location, minSalary, limit))
                .map(JobDtos.JobMatchResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Job matches")));
    }
}
