package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.domain.TechCount;
import com.techpulse.techradar.features.radar.domain.TechDateSample;
import com.techpulse.techradar.features.radar.ports.RadarGraphReadPort;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j adapter for {@link RadarGraphReadPort}. Cypher moved verbatim from the formerly-inline
 * {@code RadarAnalyticsEtlService.computeRows()}.
 */
@Component
@RequiredArgsConstructor
public class Neo4jRadarGraphReadAdapter implements RadarGraphReadPort {

    private final Driver driver;

    @Override
    public List<TechDateSample> findArticleMentionDates() {
        // Date parsing happens in Java (see FlexibleDateParser) rather than in Cypher:
        // published_date mixes ISO, dd/MM/yyyy and MM/dd/yyyy depending on crawler source, and
        // disambiguating the two slash formats needs real conditional logic, not string surgery.
        String query = "MATCH (t:Technology)<-[:MENTIONS]-(a:Article) " +
                "WHERE a.published_date IS NOT NULL " +
                "RETURN t.name AS tech, toString(a.published_date) AS raw";
        return findDateSamples(query);
    }

    @Override
    public List<TechDateSample> findJobPostingDates() {
        String query = "MATCH (t:Technology)<-[:REQUIRES]-(j:Job) " +
                "RETURN t.name AS tech, " +
                "       toString(coalesce(j.posted_date, j.due_date, j.created_at)) AS raw";
        return findDateSamples(query);
    }

    @Override
    public List<TechCount> findJobDemandSnapshot() {
        String query = "MATCH (t:Technology)<-[:REQUIRES]-(j:Job) " +
                "RETURN t.name AS tech, count(DISTINCT j) AS c";
        List<TechCount> counts = new ArrayList<>();
        try (Session session = driver.session()) {
            for (Record rec : session.run(query).list()) {
                if (!rec.get("tech").isNull()) {
                    counts.add(new TechCount(rec.get("tech").asString(), rec.get("c").asInt()));
                }
            }
        }
        return counts;
    }

    private List<TechDateSample> findDateSamples(String query) {
        List<TechDateSample> samples = new ArrayList<>();
        try (Session session = driver.session()) {
            for (Record rec : session.run(query).list()) {
                if (rec.get("tech").isNull() || rec.get("raw").isNull()) {
                    continue;
                }
                samples.add(new TechDateSample(rec.get("tech").asString(), rec.get("raw").asString()));
            }
        }
        return samples;
    }
}
