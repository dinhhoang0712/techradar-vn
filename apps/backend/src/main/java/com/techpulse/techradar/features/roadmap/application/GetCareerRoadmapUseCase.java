package com.techpulse.techradar.features.roadmap.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.graph.application.RoadAnalysisUseCase;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.job.application.GetJobMatchesUseCase;
import com.techpulse.techradar.features.job.domain.JobMatch;
import com.techpulse.techradar.features.roadmap.domain.RoadmapResult;
import com.techpulse.techradar.features.roadmap.domain.SkillRecommendation;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Personalized career roadmap: gathers /recommend (next skills to learn), /career (roadmap toward
 * a target role) and job matches for the current user in one call instead of three separate
 * page-visits (Profile, Career form, Job Matches button). Each source is already its own use
 * case/proxy call — this only orchestrates and lightly cross-references them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCareerRoadmapUseCase {

    private static final TypeReference<RoadmapResult> CACHE_TYPE = new TypeReference<>() {};
    private static final int RECOMMEND_LIMIT = 5;
    private static final int JOB_MATCH_LIMIT = 5;
    /** Caps concurrent shortestPath queries per roadmap computation (only runs on cache miss). */
    private static final int PATH_CONCURRENCY = 3;

    private final AiProxyPort aiProxyPort;
    private final UserProfileRepository userProfileRepository;
    private final GetJobMatchesUseCase getJobMatchesUseCase;
    private final RoadAnalysisUseCase roadAnalysisUseCase;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.roadmap-cache-ttl:1800}")
    private long cacheTtlSeconds;

    public Mono<RoadmapResult> execute(String userId) {
        return userProfileRepository.technologiesOf(userId)
                .flatMap(technologies -> technologies.isEmpty()
                        ? Mono.just(RoadmapResult.empty(technologies))
                        : redisCache.getOrLoadMono(
                                "cache:roadmap:" + userId,
                                Duration.ofSeconds(cacheTtlSeconds),
                                buildRoadmap(userId, technologies),
                                CACHE_TYPE));
    }

    private Mono<RoadmapResult> buildRoadmap(String userId, List<String> technologies) {
        Mono<Map<String, Object>> recommendMono = aiProxyPort
                .forward("/recommend", Map.of("user_id", userId, "limit", RECOMMEND_LIMIT), AiProxyPort.DEFAULT_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Roadmap: /recommend call failed for user {}", userId, e);
                    return Mono.just(Map.of());
                });
        Mono<Map<String, Object>> careerMono = aiProxyPort
                .forward("/career", Map.of("user_id", userId), AiProxyPort.DEFAULT_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Roadmap: /career call failed for user {}", userId, e);
                    return Mono.just(Map.of());
                });
        Mono<List<JobMatch>> jobMatchesMono = getJobMatchesUseCase.execute(userId, null, null, null, JOB_MATCH_LIMIT)
                .collectList();

        return Mono.zip(recommendMono, careerMono, jobMatchesMono)
                .flatMap(tuple -> assembleWithPaths(technologies, tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .flatMap(result -> persistTargetSkills(userId, result));
    }

    /**
     * Best-effort: persists the recommended skill names so {@code JobMatchDispatcher} can also
     * alert on new jobs matching a skill the user is learning next, not just their current
     * technologies. A failure here must never break the roadmap response, so it's swallowed the
     * same way as the /recommend, /career and road_analysis calls above.
     */
    private Mono<RoadmapResult> persistTargetSkills(String userId, RoadmapResult result) {
        List<String> skillNames = result.nextSkills().stream()
                .map(m -> SkillRecommendation.fromMap(m).techName())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skillNames.isEmpty()) {
            return Mono.just(result);
        }
        return userProfileRepository.updateTargetSkills(userId, skillNames)
                .onErrorResume(e -> {
                    log.warn("Roadmap: failed to persist target_skills for user {}", userId, e);
                    return Mono.empty();
                })
                .thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private Mono<RoadmapResult> assembleWithPaths(List<String> technologies, Map<String, Object> recommendResult,
                                                   Map<String, Object> careerResult, List<JobMatch> jobMatches) {
        List<Map<String, Object>> recommendations =
                (List<Map<String, Object>>) recommendResult.getOrDefault("recommendations", List.of());
        List<Map<String, Object>> nextSkillsBase = recommendations.stream()
                .map(item -> withJobMatchCount(item, jobMatches))
                .toList();

        // Simplification: path from the FIRST current technology only, not the best among all of
        // them — keeps the number of shortestPath queries bounded regardless of profile size.
        String sourceTech = technologies.isEmpty() ? null : technologies.get(0);
        if (sourceTech == null || nextSkillsBase.isEmpty()) {
            return Mono.just(new RoadmapResult(true, technologies, nextSkillsBase, careerResult, jobMatches));
        }

        return Flux.fromIterable(nextSkillsBase)
                .flatMap(skill -> attachPath(sourceTech, skill), PATH_CONCURRENCY)
                .collectList()
                .map(nextSkills -> new RoadmapResult(true, technologies, nextSkills, careerResult, jobMatches));
    }

    private Mono<Map<String, Object>> attachPath(String sourceTech, Map<String, Object> skill) {
        String techName = SkillRecommendation.fromMap(skill).techName();
        if (techName == null || techName.equalsIgnoreCase(sourceTech)) {
            return Mono.just(skill);
        }
        return roadAnalysisUseCase.execute(sourceTech, techName)
                .map(graphData -> {
                    if (!graphData.isFound() || graphData.getNodes().isEmpty()) {
                        return skill;
                    }
                    List<String> path = graphData.getNodes().stream()
                            .map(GraphNode::getName)
                            .filter(Objects::nonNull)
                            .toList();
                    Map<String, Object> enriched = new LinkedHashMap<>(skill);
                    enriched.put("tech_path", path);
                    return enriched;
                })
                .onErrorResume(e -> {
                    log.warn("Roadmap: road_analysis failed for {} -> {}", sourceTech, techName, e);
                    return Mono.just(skill);
                });
    }

    /** Cheap in-memory cross-reference: how many of the (already fetched) job matches are missing this tech. */
    private Map<String, Object> withJobMatchCount(Map<String, Object> recommendation, List<JobMatch> jobMatches) {
        String techName = SkillRecommendation.fromMap(recommendation).techName();
        long needingIt = techName == null ? 0 : jobMatches.stream()
                .filter(m -> m.missingSkills() != null)
                .filter(m -> m.missingSkills().stream().anyMatch(skill -> skill.equalsIgnoreCase(techName)))
                .count();
        Map<String, Object> enriched = new LinkedHashMap<>(recommendation);
        enriched.put("job_matches_needing_it", needingIt);
        return enriched;
    }
}
