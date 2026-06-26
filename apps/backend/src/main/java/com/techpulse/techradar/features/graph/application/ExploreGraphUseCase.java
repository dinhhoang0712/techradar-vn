package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Explore the knowledge graph around one or more named nodes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExploreGraphUseCase {

    private final GraphRepository graphRepository;

    public Mono<GraphData> execute(List<String> keywords, int depth, String location, Long minSalary) {
        if (keywords == null || keywords.isEmpty()) {
            log.warn("Graph explore rejected: no keywords provided");
            return Mono.error(new IllegalArgumentException("At least one keyword is required"));
        }
        int effectiveDepth = (depth < 1 || depth > 3) ? 2 : depth;
        log.info("Exploring graph for keywords={} depth={} location={} minSalary={}",
                keywords, effectiveDepth, location, minSalary);
        return graphRepository.exploreByKeywords(keywords, effectiveDepth, location, minSalary)
                .doOnSuccess(data -> log.info("Graph explore for keywords={} found={} nodes={} edges={}",
                        keywords, data.isFound(), data.getNodes().size(), data.getEdges().size()))
                .doOnError(e -> log.error("Graph explore failed for keywords={}", keywords, e));
    }
}
