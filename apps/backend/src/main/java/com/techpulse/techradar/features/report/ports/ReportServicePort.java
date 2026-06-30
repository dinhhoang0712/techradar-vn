package com.techpulse.techradar.features.report.ports;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ReportServicePort {

    Mono<Map<String, Object>> generateReport(String period, int topN, String format);
}
