package com.techpulse.techradar.features.agent.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AgentServicePort {

    Mono<Map<String, Object>> runAgent(Map<String, Object> request);
}
