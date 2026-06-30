package com.techpulse.techradar.features.career.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface CareerServicePort {

    Mono<Map<String, Object>> getCareerAdvice(Map<String, Object> request);
}
