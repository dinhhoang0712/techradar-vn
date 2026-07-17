package com.techpulse.techradar.features.social.adapters.input;

import com.techpulse.techradar.features.social.application.GetTrendingHashtagsUseCase;
import com.techpulse.techradar.features.social.ports.HashtagRepository;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Social", description = "Trending hashtags")
@RestController
@RequiredArgsConstructor
public class HashtagController {

    private final GetTrendingHashtagsUseCase getTrendingHashtagsUseCase;

    @Operation(summary = "Trending hashtags across recent posts (last 7 days)")
    @GetMapping("/hashtags/trending")
    public Mono<ResponseEntity<ApiResponse<List<TrendingHashtagResponse>>>> trending(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return getTrendingHashtagsUseCase.execute(limit)
                .map(TrendingHashtagResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Trending hashtags")));
    }

    @Value
    @Builder
    public static class TrendingHashtagResponse {
        String tag;
        long postCount;

        public static TrendingHashtagResponse from(HashtagRepository.TrendingRow row) {
            return TrendingHashtagResponse.builder()
                    .tag(row.tag())
                    .postCount(row.postCount())
                    .build();
        }
    }
}
