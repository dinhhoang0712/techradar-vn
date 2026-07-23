package com.techpulse.techradar.features.radar.realtime;

import com.techpulse.techradar.features.radar.adapters.input.RadarDtos;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Live snapshot pushed over {@code GET /radar/stream} whenever the {@code tech_analytics} ETL
 * finishes and the radar cache is evicted, so connected dashboards get fresh top4/top10 numbers
 * without a manual refresh. Same shapes as the {@code GET /radar/top4}/{@code /radar/top10}
 * REST responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadarSnapshotEvent {
    private List<RadarDtos.Top4Item> top4;
    private List<RadarDtos.Top10Item> top10;
}
