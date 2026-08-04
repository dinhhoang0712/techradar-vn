package com.techpulse.techradar.features.radar.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import com.techpulse.techradar.features.radar.domain.TechCount;
import com.techpulse.techradar.features.radar.domain.TechDateSample;
import com.techpulse.techradar.features.radar.ports.RadarGraphReadPort;
import com.techpulse.techradar.features.radar.ports.TechAnalyticsWritePort;
import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.features.system.domain.CmsContent;
import com.techpulse.techradar.shared.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests for {@link RadarAnalyticsEtlService}. The service now only wires
 * {@link RadarGraphReadPort} reads into {@link com.techpulse.techradar.features.radar.domain.TechAnalyticsTransformer}
 * and {@link TechAnalyticsWritePort} writes, so no Neo4j {@code Driver} or R2DBC
 * {@code DatabaseClient} mocking is needed here — the scoring math itself is covered separately by
 * {@code TechAnalyticsTransformerTest}. {@link TransactionalOperator} is stubbed as a pass-through
 * (real atomicity is exercised by {@code RadarOutboxIntegrationTest} against a real Postgres
 * transaction) — these tests pin *what* gets wrapped in the transaction, not the rollback itself.
 */
@ExtendWith(MockitoExtension.class)
class RadarAnalyticsEtlServiceTest {

    private static final double THRESHOLD = 30.0;

    @Mock
    private RadarGraphReadPort graphReadPort;

    @Mock
    private TechAnalyticsWritePort writePort;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private CmsService cmsService;

    private RadarAnalyticsEtlService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RadarAnalyticsEtlService(
                graphReadPort, writePort, outboxEventRepository, transactionalOperator, redisTemplate, new ObjectMapper(), cmsService);
        ReflectionTestUtils.setField(service, "trendThreshold", THRESHOLD);
        // Pass-through transaction — and lenient, since the early-failure test below never reaches
        // persistRowsAndQueueTrendAlerts (computeRows() throws first), so this stub goes unused there.
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
        // Not every test's snapshot data produces a ranked current-month row (see
        // writeKeywordDigest_* tests below for the cases that do) — lenient so the other tests
        // don't fail Mockito's unnecessary-stubbing check when this is never invoked.
        lenient().when(cmsService.create(any(), any(), any(), any())).thenReturn(Mono.just(CmsContent.builder().build()));
        lenient().when(outboxEventRepository.save(anyString(), any())).thenReturn(Mono.empty());
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

        verify(outboxEventRepository, never()).save(any(), any());
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
        // folding the snapshot in. The outbox insert happens INSIDE this same rebuild() Mono (see
        // persistRowsAndQueueTrendAlerts), so it's already done by the time rebuild() completes —
        // no async timeout needed, unlike the old fire-and-forget Kafka publish this replaced.
        StepVerifier.create(service.rebuild()).expectNext(2L).verifyComplete();

        ArgumentCaptor<TrendAlertEvent> captor = ArgumentCaptor.forClass(TrendAlertEvent.class);
        verify(outboxEventRepository).save(eq(KafkaTopicConstants.TREND_ALERTS), captor.capture());
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

        verify(outboxEventRepository, never()).save(any(), any());
    }

    @Test
    void rebuild_propagatesFailure_whenOutboxInsertFails() {
        // Proves persistRowsAndQueueTrendAlerts wraps BOTH the upsert and the outbox insert in the
        // transactional operator: if queuing the alert fails, the whole rebuild() must fail too —
        // in a real transaction (see RadarOutboxIntegrationTest) that means the tech_analytics
        // upsert rolls back with it, so the two can never disagree about whether the alert happened.
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates())
                .thenReturn(List.of(new TechDateSample("Java", previous.atDay(10).toString())));
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of(new TechCount("Java", 20)));
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));
        when(outboxEventRepository.save(eq(KafkaTopicConstants.TREND_ALERTS), any()))
                .thenReturn(Mono.error(new RuntimeException("db unreachable")));

        StepVerifier.create(service.rebuild()).expectError(RuntimeException.class).verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuild_writesRunningThenIdleStatusToRedis() {
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(eq("radar:status"), anyString())).thenReturn(Mono.just(true));
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates()).thenReturn(List.of());
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of());

        StepVerifier.create(service.rebuild()).expectNext(0L).verifyComplete();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(2)).set(eq("radar:status"), jsonCaptor.capture());
        assertThat(jsonCaptor.getAllValues().get(0)).contains("\"state\":\"running\"");
        assertThat(jsonCaptor.getAllValues().get(1))
                .contains("\"state\":\"idle\"")
                .contains("\"rowsUpserted\":0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuild_stillWritesIdleStatus_whenTheEtlItselfFails() {
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(eq("radar:status"), anyString())).thenReturn(Mono.just(true));
        when(graphReadPort.findArticleMentionDates()).thenThrow(new RuntimeException("Neo4j unreachable"));

        StepVerifier.create(service.rebuild()).expectError(RuntimeException.class).verify();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, timeout(1000).times(2)).set(eq("radar:status"), jsonCaptor.capture());
        assertThat(jsonCaptor.getAllValues().get(1)).contains("\"state\":\"idle\"").contains("\"rowsUpserted\":null");
    }

    @Test
    void rebuild_writesCmsKeywordDigest_rankedByDemandForCurrentMonthOnly() {
        YearMonth current = YearMonth.now();
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates()).thenReturn(List.of(
                new TechDateSample("Java", current.atDay(5).toString()),
                new TechDateSample("Python", current.atDay(5).toString())));
        // Both have 1 job posting this month, but ranking is by CURRENT DEMAND SNAPSHOT, not
        // posting count -> Python (20) outranks Java (5) despite the tie in postings.
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of(
                new TechCount("Java", 5), new TechCount("Python", 20)));
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.rebuild()).expectNext(2L).verifyComplete();

        verify(cmsService, timeout(1000)).create(
                eq("Từ khóa nổi bật: Python, Java"), eq("Keyword"), eq(current.atDay(1)), eq("Analyzed"));
    }

    @Test
    void rebuild_skipsCmsKeywordDigest_whenNoCurrentMonthRowHasARanking() {
        // No job-demand snapshot -> rankByDemand() has nothing to rank, so every row's rank is
        // null (see TechAnalyticsTransformer.buildRows) -> nothing to summarize this month.
        when(graphReadPort.findArticleMentionDates()).thenReturn(List.of());
        when(graphReadPort.findJobPostingDates()).thenReturn(List.of(
                new TechDateSample("Java", YearMonth.now().atDay(5).toString())));
        when(graphReadPort.findJobDemandSnapshot()).thenReturn(List.of());
        when(writePort.upsert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.rebuild()).expectNext(1L).verifyComplete();

        verify(cmsService, never()).create(any(), any(), any(), any());
    }
}
