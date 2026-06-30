package com.techpulse.techradar.features.forecast.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ForecastServicePort {

    Mono<Map<String, Object>> getForecast(String technology, int horizonMonths);
}
