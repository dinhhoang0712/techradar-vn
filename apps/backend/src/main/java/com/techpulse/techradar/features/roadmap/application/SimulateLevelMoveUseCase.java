package com.techpulse.techradar.features.roadmap.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.roadmap.domain.LevelMoveResult;
import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.domain.UserProfiles;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * "What if I moved to this experience level?" — previews the effect of a hypothetical
 * Intern/Fresher/Junior/Middle/Senior/Lead level on the user's job-match count (same skills,
 * different level filter) and the market salary at that level, without persisting anything to
 * the profile. Sibling of {@link SimulateCareerMoveUseCase} ("what if I learned this
 * technology?") — same shape, minus a forecast (trend forecasting is tech-specific, there is no
 * equivalent concept for a level).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulateLevelMoveUseCase {

    private static final int MATCH_SAMPLE_LIMIT = 100;
    private static final TypeReference<LevelMoveResult> CACHE_TYPE = new TypeReference<>() {};
    private static final List<String> VALID_LEVELS =
            List.of("Intern", "Fresher", "Junior", "Middle", "Senior", "Lead");

    private final UserProfileRepository userProfileRepository;
    private final GetJobMatchesUseCase getJobMatchesUseCase;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.roadmap-cache-ttl:1800}")
    private long cacheTtlSeconds;

    public Mono<LevelMoveResult> execute(String userId, String targetLevel) {
        String level = targetLevel == null ? "" : targetLevel.trim();
        if (level.isBlank()) {
            return Mono.error(new IllegalArgumentException("targetLevel is required"));
        }
        if (!VALID_LEVELS.contains(level)) {
            return Mono.error(new IllegalArgumentException("Unknown level: " + level));
        }

        return userProfileRepository.findByUserId(userId)
                .defaultIfEmpty(UserProfile.builder().build())
                .flatMap(profile -> redisCache.getOrLoadMono(
                        "cache:simulate:level:" + userId + ":" + level.toLowerCase(Locale.ROOT),
                        Duration.ofSeconds(cacheTtlSeconds),
                        buildSimulation(UserProfiles.technologiesOrEmpty(profile), profile.getCurrentLevel(), level),
                        CACHE_TYPE));
    }

    private Mono<LevelMoveResult> buildSimulation(List<String> skills, String currentLevel, String targetLevel) {
        // currentLevel null/blank -> unfiltered (matches original "at any level" job-match count),
        // an honest baseline when the user hasn't set their profile level yet.
        Mono<List<JobMatch>> currentMatchesMono =
                getJobMatchesUseCase.executeForSkills(skills, currentLevel, MATCH_SAMPLE_LIMIT).collectList();
        Mono<List<JobMatch>> simulatedMatchesMono =
                getJobMatchesUseCase.executeForSkills(skills, targetLevel, MATCH_SAMPLE_LIMIT).collectList();

        return Mono.zip(currentMatchesMono, simulatedMatchesMono)
                .map(tuple -> new LevelMoveResult(
                        currentLevel,
                        targetLevel,
                        tuple.getT1().size(),
                        tuple.getT2().size(),
                        salaryFromMatches(targetLevel, tuple.getT2())));
    }

    /**
     * Builds a {@link SalaryInsight} from the already-fetched simulated matches' parsed salary
     * fields, instead of a fresh SQL query — {@link JobMatch#salaryMinVnd()}/{@code
     * salaryMaxVnd()} are already computed midpoints of each job's free-text salary
     * ({@code SalaryParser}, see {@code GetJobMatchesUseCase.toJobMatch}), so no re-parsing needed.
     */
    private SalaryInsight salaryFromMatches(String level, List<JobMatch> matches) {
        List<Double> midpoints = matches.stream()
                .filter(m -> m.salaryMinVnd() != null && m.salaryMaxVnd() != null)
                .map(m -> (m.salaryMinVnd() + m.salaryMaxVnd()) / 2.0)
                .toList();
        return SalaryInsight.fromMidpoints(level, matches.size(), midpoints, List.of());
    }
}
