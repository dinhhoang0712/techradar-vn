package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.auth.ports.UserStatsRepository;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Job/tech market metrics for the admin dashboard: indexed jobs, top demanded technologies, how
 * many job-match alerts have been sent out, and adoption of the experience-level enum (job-side
 * distribution + how many users have set their own {@code current_level}).
 */
@Component
@RequiredArgsConstructor
public class JobMarketMetricsService {

    private static final int TOP_N = 10;

    private final JobRepository jobRepository;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserStatsRepository userStatsRepository;

    public Mono<JobMarketStats> jobMarket() {
        return Mono.zip(
                jobRepository.countJobs(),
                jobRepository.topTechnologies(TOP_N).map(TechDemand::from).collectList(),
                notificationRepository.countGroupedByType().collectList(),
                jobRepository.jobsByLevel().map(LevelDemand::from).collectList(),
                Mono.zip(userProfileRepository.countWithCurrentLevel(), userStatsRepository.countAll())
        ).map(t -> JobMarketStats.builder()
                .totalJobsIndexed(t.getT1())
                .topTechnologies(t.getT2())
                .jobMatchAlertsSent(t.getT3().stream()
                        .filter(tc -> "JOB_MATCH".equals(tc.type()))
                        .mapToLong(NotificationRepository.TypeCount::count)
                        .findFirst().orElse(0L))
                .jobsByLevel(t.getT4())
                .usersWithCurrentLevel(t.getT5().getT1())
                .totalUsers(t.getT5().getT2())
                .build());
    }

    @Value
    @Builder
    public static class JobMarketStats {
        long totalJobsIndexed;
        List<TechDemand> topTechnologies;
        long jobMatchAlertsSent;
        List<LevelDemand> jobsByLevel;
        long usersWithCurrentLevel;
        long totalUsers;
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

    @Value
    @Builder
    public static class LevelDemand {
        String level;
        long jobCount;

        static LevelDemand from(JobRepository.LevelDemandRaw r) {
            return LevelDemand.builder().level(r.level()).jobCount(r.jobCount()).build();
        }
    }
}
