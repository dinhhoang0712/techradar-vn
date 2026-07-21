package com.techpulse.techradar.features.radar.domain;

/**
 * Current total job-demand snapshot for one technology — a distinct job count read straight from
 * the graph, not yet folded into a month or ranked.
 */
public record TechCount(String tech, int count) {
}
