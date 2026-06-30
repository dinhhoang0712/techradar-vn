package com.techpulse.techradar.features.recommend.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface RecommendServicePort {

    Mono<Map<String, Object>> getRecommendations(Map<String, Object> request);
}
