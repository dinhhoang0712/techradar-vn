package com.techpulse.techradar.features.radar.ports;

import com.techpulse.techradar.features.radar.domain.TechCount;
import com.techpulse.techradar.features.radar.domain.TechDateSample;

import java.util.List;

/**
 * Read port over the Neo4j knowledge graph for the raw signals feeding the {@code tech_analytics}
 * ETL rebuild. Distinct from {@link RadarQueryRepository}, which reads the already-built
 * {@code tech_analytics} time series for the radar/compare features — this port reads the graph
 * that {@code tech_analytics} is rebuilt from.
 */
public interface RadarGraphReadPort {

    /** Article-mention dates per technology (raw, unparsed date strings). */
    List<TechDateSample> findArticleMentionDates();

    /** Job-posting dates per technology, where a date exists on the job node. */
    List<TechDateSample> findJobPostingDates();

    /** Current total job-demand snapshot per technology (distinct job count). */
    List<TechCount> findJobDemandSnapshot();
}
