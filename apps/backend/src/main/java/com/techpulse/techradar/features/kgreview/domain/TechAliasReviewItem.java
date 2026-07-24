package com.techpulse.techradar.features.kgreview.domain;

import java.time.LocalDateTime;

/**
 * One pending row from {@code dp_tech_alias_review_queue} — a Technology name pair the LLM in
 * {@code data-platform/gold/tech_dedup.py} Stage B was not confident enough to auto-merge.
 * <p>
 * By that job's own writer convention, {@code nameB} is always the LLM's suggested canonical
 * name and {@code nameA} the duplicate to fold into it (see {@code tech_dedup.py}'s
 * {@code review_entries.append({"name_a": n, "name_b": canonical, ...})}) — an admin approving
 * this item may still override which side is canonical.
 */
public record TechAliasReviewItem(
        long id,
        String nameA,
        String nameB,
        String llmReasoning,
        String status,
        LocalDateTime createdAt
) {
}
