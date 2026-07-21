package com.techpulse.techradar.features.radar.etl;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import com.techpulse.techradar.features.radar.domain.TechCount;
import com.techpulse.techradar.features.radar.domain.TechDateSample;
import com.techpulse.techradar.features.radar.ports.RadarGraphReadPort;
import com.techpulse.techradar.features.radar.ports.TechAnalyticsWritePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests for {@link RadarAnalyticsEtlService}. The service now only wires
 * {@link RadarGraphReadPort} reads into {@link com.techpulse.techradar.features.radar.domain.TechAnalyticsTransformer}
 * and {@link TechAnalyticsWritePort} writes, so no Neo4j {@code Driver} or R2DBC
 * {@code DatabaseClient} mocking is needed here — the scoring math itself is covered separately by
 * {@code TechAnalyticsTransformerTest}.
 */
@ExtendWith(MockitoExtension.class)
class RadarAnalyticsEtlServiceTest {

    private static final double THRESHOLD = 30.0;

    @Mock
    private RadarGraphReadPort graphReadPort;

    @Mock
    private TechAnalyticsWritePort writePort;

    @Mock
    private KafkaProducerService kafkaProducer;

    private RadarAnalyticsEtlService service;

    @BeforeEach
    void setUp() {
        service = new RadarAnalyticsEtlService(graphReadPort, writePort, kafkaProducer);
        ReflectionTestUtils.setField(service, "trendThreshold", THRESHOLD);
    }

    @Test
    void rebuild_readsGraphTransformsAndUpsertsEveryComputedRow() {
        YearMonth current = YearMonth.now();
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates()).thenReturn(List.of(
                new TechDateSample("Java", current.atDay(5).toString()),
                new TechDateSample("Java", current.atDay(20).toString())));
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of());
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        // Only the current month has data and there's no prior month to compare against, so mom
        // growth is 0% — below the alert threshold, and jobCount() > 0 -> exactly one upsert.
        StepVerifier.create(service.rebuild()).expectNext(1L).verifyComplete();

        ArgumentCaptor<TechAnalyticsRow> captor = ArgumentCaptor.forClass(TechAnalyticsRow.class);
        verify(writePort).upsert(captor.capture());
        TechAnalyticsRow row = captor.getValue();
        assertThat(row.tech()).isEqualTo("Java");
        assertThat(row.jobCount()).isEqualTo(2);
        assertThat(row.month()).isEqualTo(current.atDay(1));

        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void rebuild_sumsRowsUpdatedAcrossAllUpserts() {
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates()).thenReturn(List.of(
                new TechDateSample("Java", YearMonth.now().atDay(5).toString()),
                new TechDateSample("Python", YearMonth.now().atDay(5).toString())));
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of());
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.rebuild()).expectNext(2L).verifyComplete();
    }

    @Test
    void rebuild_publishesTrendAlertWhenCurrentMonthMomGrowthMeetsThreshold() {
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        // One posting last month (activity = 1), the current-demand snapshot jumps to 20 ->
        // (20-1)/1*100 = 1900% MoM, comfortably above the 30% threshold.
        when(graphReadPort.findJobPostingDates())
                .thenReturn(List.of(new TechDateSample("Java", previous.atDay(10).toString())));
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of(new TechCount("Java", 20)));
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        // Two rows: the historical previous-month row plus the current-month row created by
        // folding the snapshot in.
        StepVerifier.create(service.rebuild()).expectNext(2L).verifyComplete();

        ArgumentCaptor<TrendAlertEvent> captor = ArgumentCaptor.forClass(TrendAlertEvent.class);
        verify(kafkaProducer, timeout(2000)).send(eq(KafkaTopicConstants.TREND_ALERTS), captor.capture());
        TrendAlertEvent event = captor.getValue();
        assertThat(event.getTechnology()).isEqualTo("Java");
        assertThat(event.getJobCount()).isEqualTo(20);
        assertThat(event.getMomRate()).isEqualTo(1900.0);
    }

    @Test
    void rebuild_skipsTrendAlertWhenMomGrowthBelowThreshold() {
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates())
                .thenReturn(List.of(new TechDateSample("Java", previous.atDay(10).toString())));
        // Snapshot equals last month's activity -> 0% MoM growth, below the threshold.
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of(new TechCount("Java", 1)));
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.rebuild()).expectNext(2L).verifyComplete();

        verifyNoInteractions(kafkaProducer);
    }
}
