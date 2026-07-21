package com.techpulse.techradar.features.roadmap.domain;

import java.util.Map;

/**
 * Typed view over the {@code tech_name}/{@code growth_rate} keys of one raw recommendation item
 * from ai-rag-core's {@code /recommend} response (see {@code RecommendItem} in
 * {@code services/ai-rag-core/app/api/schemas.py}, which also carries {@code reason}, {@code ring},
 * {@code co_occurrence} and {@code confidence} — fields no Java code reads today, only passed
 * through to the frontend as-is).
 * <p>
 * {@link com.techpulse.techradar.features.roadmap.domain.RoadmapResult#nextSkills()} deliberately
 * stays {@code List<Map<String, Object>>} rather than {@code List<SkillRecommendation>}: it's both
 * the Redis-cached shape and the {@code /career/roadmap} response shape, and narrowing it to this
 * record's two known fields would silently drop the pass-through fields above from the API
 * response. This record instead replaces the untyped {@code Map.get("tech_name")} /
 * {@code Map.get("growth_rate")} look-ups at each read site (
 * {@code GetCareerRoadmapUseCase.attachPath}/{@code withJobMatchCount}, and
 * {@code RoadmapAlertService.topHotSkill}) with one typed parse.
 */
public record SkillRecommendation(String techName, double growthRate) {

    public static SkillRecommendation fromMap(Map<String, Object> raw) {
        Object techNameObj = raw.get("tech_name");
        Object growthRateObj = raw.get("growth_rate");
        return new SkillRecommendation(
                techNameObj == null ? null : String.valueOf(techNameObj),
                growthRateObj instanceof Number n ? n.doubleValue() : 0.0
        );
    }
}
