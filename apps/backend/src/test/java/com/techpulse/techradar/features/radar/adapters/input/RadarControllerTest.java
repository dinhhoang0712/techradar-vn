package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.application.GetTopTechnologiesUseCase;
import com.techpulse.techradar.features.radar.application.SearchTrendUseCase;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.RadarExporter;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import com.techpulse.techradar.features.radar.realtime.RadarBroadcaster;
import com.techpulse.techradar.features.radar.realtime.RadarSnapshotEvent;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarControllerTest {

    @Mock
    private GetTopTechnologiesUseCase getTopTechnologiesUseCase;
    @Mock
    private SearchTrendUseCase searchTrendUseCase;
    @Mock
    private RadarExporter radarExporter;
    @Mock
    private RadarBroadcaster radarBroadcaster;

    private RadarController controller;

    @BeforeEach
    void setUp() {
        controller = new RadarController(getTopTechnologiesUseCase, searchTrendUseCase, radarExporter, radarBroadcaster);
    }

    @Test
    void getTop4_mapsSnapshotsToTop4Items() {
        when(getTopTechnologiesUseCase.execute(4)).thenReturn(Flux.just(
                new TechSnapshot("Kotlin", 120, 45.0, 32.0, 40)));

        StepVerifier.create(controller.getTop4())
                .assertNext(response -> {
                    ApiResponse<List<RadarDtos.Top4Item>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).hasSize(1);
                    RadarDtos.Top4Item item = body.getData().get(0);
                    assertThat(item.getIndustry()).isEqualTo("Kotlin");
                    assertThat(item.getJobCount()).isEqualTo(120);
                    assertThat(item.getGrowthRate()).isEqualTo(45.0);
                    assertThat(item.getMomRate()).isEqualTo(32.0);
                    assertThat(item.getJobsThisMonth()).isEqualTo(40);
                })
                .verifyComplete();
    }

    @Test
    void getTop10_mapsSnapshotsToTop10Items() {
        when(getTopTechnologiesUseCase.execute(10)).thenReturn(Flux.just(
                new TechSnapshot("Rust", 80, 20.0, 5.0, 10)));

        StepVerifier.create(controller.getTop10())
                .assertNext(response -> {
                    ApiResponse<List<RadarDtos.Top10Item>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getKeyword()).isEqualTo("Rust");
                    assertThat(body.getData().get(0).getJobCount()).isEqualTo(80);
                })
                .verifyComplete();
    }

    @Test
    void stream_forwardsBroadcasterSnapshotsAsRadarSnapshotServerSentEvents() {
        RadarSnapshotEvent event = new RadarSnapshotEvent(
                List.of(new RadarDtos.Top4Item("Kotlin", 45.0, 120, 32.0, 40)),
                List.of(new RadarDtos.Top10Item("Kotlin", 120)));
        when(radarBroadcaster.stream()).thenReturn(Flux.just(event));

        StepVerifier.create(controller.stream().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("radar-snapshot");
                    assertThat(sse.data()).isEqualTo(event);
                })
                .verifyComplete();
    }

    @Test
    void search_groupsMonthlyRowsByYearMonthIntoOneTrendPointWithAKeywordCountMap() {
        when(searchTrendUseCase.execute(List.of("java", "go"), 6)).thenReturn(Flux.just(
                new MonthlyCount("java", 2026, 7, 100, 20, 5.0, 2.0, 3.0),
                new MonthlyCount("go", 2026, 7, 50, 10, 1.0, 1.0, 1.0)));

        StepVerifier.create(controller.search(List.of("java", "go"), 6))
                .assertNext(response -> {
                    ApiResponse<List<RadarDtos.TrendPoint>> body = response.getBody();
                    assertThat(body.getData()).hasSize(1);
                    RadarDtos.TrendPoint point = body.getData().get(0);
                    assertThat(point.getMonth()).isEqualTo(7);
                    assertThat(point.getYear()).isEqualTo(2026);
                    assertThat(point.getKeywords()).containsEntry("java", 100).containsEntry("go", 50);
                })
                .verifyComplete();
    }

    @Test
    void search_returns400_whenUseCaseErrors() {
        when(searchTrendUseCase.execute(List.of("java"), 6))
                .thenReturn(Flux.error(new IllegalArgumentException("boom")));

        StepVerifier.create(controller.search(List.of("java"), 6))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody().isSuccess()).isFalse();
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SEARCH_ERROR");
                })
                .verifyComplete();
    }

    @Test
    void exportPng_rendersTopTechnologiesAsPngWithAttachmentHeaders() {
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G'};
        when(getTopTechnologiesUseCase.execute(20)).thenReturn(Flux.just(new TechSnapshot("Java", 100, 10.0, 5.0, 20)));
        when(radarExporter.toPng(List.of(new TechSnapshot("Java", 100, 10.0, 5.0, 20)))).thenReturn(pngBytes);

        StepVerifier.create(controller.exportPng(20))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("radar.png");
                    assertThat(response.getBody()).isEqualTo(pngBytes);
                })
                .verifyComplete();
    }

    @Test
    void exportCsv_rendersTopTechnologiesAsCsvWithAttachmentHeaders() {
        byte[] csvBytes = "technology_name,job_count,growth_rate\n".getBytes();
        when(getTopTechnologiesUseCase.execute(50)).thenReturn(Flux.just(new TechSnapshot("Java", 100, 10.0, 5.0, 20)));
        when(radarExporter.toCsv(List.of(new TechSnapshot("Java", 100, 10.0, 5.0, 20)))).thenReturn(csvBytes);

        StepVerifier.create(controller.exportCsv(50))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("radar.csv");
                    assertThat(response.getBody()).isEqualTo(csvBytes);
                })
                .verifyComplete();
    }
}
