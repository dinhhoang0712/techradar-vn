package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Job/tech market metrics for the admin dashboard: indexed jobs, top demanded technologies, and
 * how many job-match alerts have been sent out.
 */
@Component
@RequiredArgsConstructor
public class JobMarketMetricsService {

    private static final int TOP_N = 10;

    private final JobRepository jobRepository;
    private final NotificationRepository notificationRepository;

    public Mono<JobMarketStats> jobMarket() {
        return Mono.zip(
                jobRepository.countJobs(),
                jobRepository.topTechnologies(TOP_N).map(TechDemand::from).collectList(),
                notificationRepository.countGroupedByType().collectList()
        ).map(t -> JobMarketStats.builder()
                .totalJobsIndexed(t.getT1())
                .topTechnologies(t.getT2())
                .jobMatchAlertsSent(t.getT3().stream()
                        .filter(tc -> "JOB_MATCH".equals(tc.type()))
                        .mapToLong(NotificationRepository.TypeCount::count)
                        .findFirst().orElse(0L))
                .build());
    }

    @Value
    @Builder
    public static class JobMarketStats {
        long totalJobsIndexed;
        List<TechDemand> topTechnologies;
        long jobMatchAlertsSent;
    }

    @Value
    @Builder
    public static class TechDemand {
        String name;
        long jobCount;

        static TechDemand from(JobRepository.TechDemandRaw r) {
            return TechDemand.builder().name(r.name()).jobCount(r.jobCount()).build();
        }
    }
}
