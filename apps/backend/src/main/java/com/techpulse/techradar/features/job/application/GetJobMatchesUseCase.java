package com.techpulse.techradar.features.job.application;

import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.job.ports.JobRepository;
import com.techpulse.techradar.features.salary.domain.SalaryParser;
import com.techpulse.techradar.features.salary.domain.SalaryRange;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

    // Fetch more than requested from Neo4j since location/min-salary filtering happens after the
    // graph query (location lives on Company, salary is free-text parsed post-hoc).
    private static final int RAW_FETCH_MULTIPLIER = 3;

    private final JobRepository jobRepository;
    private final UserProfileRepository userProfileRepository;

    public Flux<JobMatch> execute(String userId, String location, Double minSalaryMVnd, int limit) {
        int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, 100);

        return userProfileRepository.findByUserId(userId)
                .map(profile -> profile.getTechnologies() == null ? List.<String>of() : profile.getTechnologies())
                .defaultIfEmpty(List.of())
                .flatMapMany(skills -> matchJobs(skills, location, minSalaryMVnd, effectiveLimit));
    }

    private Flux<JobMatch> matchJobs(List<String> skills, String location, Double minSalaryMVnd, int limit) {
        List<String> skillsLower = skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        if (skillsLower.isEmpty()) {
            return Flux.empty();
        }

        return jobRepository.findMatchingJobs(skillsLower, limit * RAW_FETCH_MULTIPLIER)
                .map(this::toJobMatch)
                .filter(match -> matchesLocation(match, location))
                .filter(match -> matchesMinSalary(match, minSalaryMVnd))
                .sort((a, b) -> Double.compare(b.score(), a.score()))
                .take(limit);
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
