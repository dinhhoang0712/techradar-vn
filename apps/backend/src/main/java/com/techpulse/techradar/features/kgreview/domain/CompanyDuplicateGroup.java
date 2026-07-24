package com.techpulse.techradar.features.kgreview.domain;

import java.util.List;

/**
 * A group of {@code Company} nodes suspected to be the same real-world organization under
 * different legal-entity naming (e.g. "FPT Software" vs "Công Ty Cổ Phần Viễn Thông FPT") —
 * detected live from Neo4j (never persisted), mirroring the heuristic in
 * {@code data-platform/gold/kg_health_audit.py}'s {@code _check_company_near_duplicates}.
 * <p>
 * This is a REVIEW candidate, not a confirmed duplicate — merging Company nodes incorrectly
 * corrupts {@code job_count}/{@code company_size} aggregates, a materially higher-risk mistake
 * than merging a Technology name variant, so no automatic merge ever happens for this group.
 */
public record CompanyDuplicateGroup(
        String normalizedCore,
        List<Candidate> companies
) {
    public record Candidate(String id, String name) {
    }
}
