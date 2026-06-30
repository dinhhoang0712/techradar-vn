package com.techpulse.techradar.features.summarize.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface SummarizeServicePort {

    Mono<Map<String, Object>> summarize(Map<String, Object> request);
}
