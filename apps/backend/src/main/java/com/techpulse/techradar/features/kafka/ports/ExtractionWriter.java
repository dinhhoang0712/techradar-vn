package com.techpulse.techradar.features.kafka.ports;

import com.techpulse.techradar.features.kafka.event.ExtractedArticle;
import com.techpulse.techradar.features.kafka.event.ExtractedJob;

/** Persists entity-extracted crawler output to the knowledge graph. */
public interface ExtractionWriter {

    void writeArticle(ExtractedArticle article);

    /**
     * @return {@code true} if this call is the one that created the {@code Job} node (a brand-new
     * posting), {@code false} if it updated an already-known job.
     */
    boolean writeJob(ExtractedJob job);
}
