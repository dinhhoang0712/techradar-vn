package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.application.GetTopTechnologiesUseCase;
import com.techpulse.techradar.features.radar.application.SearchTrendUseCase;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Radar API controller. Shapes match what the web/mobile clients consume; data comes from the
 * {@code tech_analytics} time-series store.
 */
@Tag(name = "Radar", description = "Technology trend radar endpoints")
@RestController
@RequestMapping("/radar")
@RequiredArgsConstructor
public class RadarController {

    private final GetTopTechnologiesUseCase getTopTechnologiesUseCase;
    private final SearchTrendUseCase searchTrendUseCase;
    private final RadarExporter radarExporter;

    @Operation(summary = "Get top 4 growing technologies")
    @GetMapping("/top4")
    public Mono<ResponseEntity<ApiResponse<List<RadarDtos.Top4Item>>>> getTop4() {
        return getTopTechnologiesUseCase.execute(4)
                .map(t -> new RadarDtos.Top4Item(
                        t.name(), t.growthRate(), t.jobCount(), t.momRate(), t.jobsThisMonth()))
                .collectList()
                .map(items -> ResponseEntity.ok(ApiResponse.success(items, "Top 4 technologies")));
    }

    @Operation(summary = "Get top 10 technologies")
    @GetMapping("/top10")
    public Mono<ResponseEntity<ApiResponse<List<RadarDtos.Top10Item>>>> getTop10() {
        return getTopTechnologiesUseCase.execute(10)
                .map(t -> new RadarDtos.Top10Item(t.name(), t.jobCount()))
                .collectList()
                .map(items -> ResponseEntity.ok(ApiResponse.success(items, "Top 10 technologies")));
    }

    @Operation(summary = "Monthly trend for one or more technologies")
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<RadarDtos.TrendPoint>>>> search(
            @RequestParam List<String> keywords,
            @RequestParam(defaultValue = "6") int months
    ) {
        return searchTrendUseCase.execute(keywords, months)
                .collectList()
                .map(rows -> ResponseEntity.ok(ApiResponse.success(toTrendPoints(rows), "Trend data")))
                .onErrorResume(ex -> Mono.just(ResponseEntity.badRequest()
                        .body(ApiResponse.error(ex.getMessage(), "SEARCH_ERROR"))));
    }

    @Operation(summary = "Export top technologies as PNG")
    @GetMapping(value = "/export-png", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> exportPng(@RequestParam(defaultValue = "20") int limit) {
        return getTopTechnologiesUseCase.execute(limit)
                .collectList()
                .map(trends -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"radar.png\"")
                        .contentType(MediaType.IMAGE_PNG)
                        .body(radarExporter.toPng(trends)));
    }

    @Operation(summary = "Export top technologies as CSV")
    @GetMapping(value = "/export-csv", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> exportCsv(@RequestParam(defaultValue = "50") int limit) {
        return getTopTechnologiesUseCase.execute(limit)
                .collectList()
                .map(trends -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"radar.csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(radarExporter.toCsv(trends)));
    }

    /** Group monthly rows into one point per (year, month) with a {tech: count} map. */
    private List<RadarDtos.TrendPoint> toTrendPoints(List<MonthlyCount> rows) {
        Map<String, RadarDtos.TrendPoint> byMonth = new LinkedHashMap<>();
        for (MonthlyCount row : rows) {
            String key = row.year() + "-" + row.month();
            RadarDtos.TrendPoint point = byMonth.computeIfAbsent(key,
                    k -> new RadarDtos.TrendPoint(row.month(), row.year(), new LinkedHashMap<>()));
            point.getKeywords().put(row.name(), row.activity());
        }
        return new ArrayList<>(byMonth.values());
    }
}
