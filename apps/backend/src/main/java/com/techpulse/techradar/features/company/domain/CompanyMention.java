package com.techpulse.techradar.features.company.domain;

/**
 * An article mentioning a company, via the Article-[:MENTIONS]->Company relationship written by
 * the ingestion pipeline (KafkaNeo4jWriterService.writeArticle / data-platform gold/neo4j_article_sync.py).
 */
public record CompanyMention(
        String id,
        String title,
        String url,
        String publishDate,
        String sourcePlatform
) {
}
