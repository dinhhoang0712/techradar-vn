package com.techpulse.techradar.features.job.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.salary.domain.SalaryParser;
import com.techpulse.techradar.features.salary.domain.SalaryRange;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jobs matching the current user's profile skills, ranked by skill-overlap score.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetJobMatchesUseCase {

    private static final int MAX_LIMIT = 100;
    // Fetch more than the max requested limit from Neo4j since location/min-salary filtering
    // happens after the graph query (location lives on Company, salary is free-text parsed
    // post-hoc). Fixed at the max so the cached raw fetch (see rawMatches) can serve any limit.
    private static final int RAW_FETCH_MULTIPLIER = 3;
    private static final int RAW_FETCH_SIZE = MAX_LIMIT * RAW_FETCH_MULTIPLIER;
    private static final TypeReference<List<JobMatch>> LIST_TYPE = new TypeReference<>() {};

    private final JobRepository jobRepository;
    private final UserProfileRepository userProfileRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.job-cache-ttl:1800}")
    private long cacheTtlSeconds;

    public Flux<JobMatch> execute(String userId, String location, Double minSalaryMVnd, int limit) {
        int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, MAX_LIMIT);

        return userProfileRepository.findByUserId(userId)
                .map(profile -> profile.getTechnologies() == null ? List.<String>of() : profile.getTechnologies())
                .defaultIfEmpty(List.of())
                .flatMapMany(skills -> matchJobs(skills, location, minSalaryMVnd, effectiveLimit));
    }

    /**
     * Same scoring as {@link #execute}, against an explicit skill list instead of a stored
     * profile — used by the "what-if" career simulator to preview a hypothetical skill without
     * persisting it to the user's profile.
     */
    public Flux<JobMatch> executeForSkills(List<String> skills, int limit) {
        int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, MAX_LIMIT);
        return matchJobs(skills == null ? List.of() : skills, null, null, effectiveLimit);
    }

    private Flux<JobMatch> matchJobs(List<String> skills, String location, Double minSalaryMVnd, int limit) {
        List<String> skillsLower = skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        if (skillsLower.isEmpty()) {
            return Flux.empty();
        }

        return rawMatches(skillsLower)
                .filter(match -> matchesLocation(match, location))
                .filter(match -> matchesMinSalary(match, minSalaryMVnd))
                .take(limit);
    }

    /**
     * Neo4j fetch + scoring, cached per distinct skill set (location/min-salary are filtered
     * afterwards so they don't fragment the cache key, and any requested limit can be served
     * from the same cached raw set).
     */
    private Flux<JobMatch> rawMatches(List<String> skillsLower) {
        String cacheKey = "cache:job:match:" + String.join("|", skillsLower);
        return redisCache.getOrLoad(
                cacheKey,
                Duration.ofSeconds(cacheTtlSeconds),
                jobRepository.findMatchingJobs(skillsLower, RAW_FETCH_SIZE)
                        .map(this::toJobMatch)
                        .sort((a, b) -> Double.compare(b.score(), a.score())),
                LIST_TYPE
        );
    }

    private JobMatch toJobMatch(JobRepository.JobMatchRaw raw) {
        List<String> matched = dedupeCaseInsensitive(raw.matched());
        Set<String> matchedLower = matched.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (String required : dedupeCaseInsensitive(raw.required())) {
            if (!matchedLower.contains(required.toLowerCase(Locale.ROOT))) {
                missing.add(required);
            }
        }

        SalaryRange range = SalaryParser.parse(raw.salary()).orElse(null);

        return new JobMatch(
                raw.title(),
                raw.company(),
                raw.location(),
                raw.salary(),
                range != null ? range.minVnd() : null,
                range != null ? range.maxVnd() : null,
                raw.sourceUrl(),
                parseDate(raw.dueDate()),
                matched,
                missing,
                raw.score()
        );
    }

    private static List<String> dedupeCaseInsensitive(List<String> names) {
        Set<String> seenLower = new java.util.LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (seenLower.add(name.toLowerCase(Locale.ROOT))) {
                result.add(name);
            }
        }
        return result;
    }

    private boolean matchesLocation(JobMatch match, String location) {
        if (location == null || location.isBlank()) return true;
        return match.location() != null
                && match.location().toLowerCase(Locale.ROOT).contains(location.toLowerCase(Locale.ROOT));
    }

    private boolean matchesMinSalary(JobMatch match, Double minSalaryMVnd) {
        if (minSalaryMVnd == null) return true;
        return match.salaryMaxVnd() != null && match.salaryMaxVnd() >= minSalaryMVnd;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
        } catch (Exception e) {
            log.debug("Unparsable job due_date: {}", raw);
            return null;
        }
    }
}
