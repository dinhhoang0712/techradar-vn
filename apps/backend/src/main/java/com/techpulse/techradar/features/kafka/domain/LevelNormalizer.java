package com.techpulse.techradar.features.kafka.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chuẩn hoá free-text "level" (job posting) về 1 trong 6 mức kinh nghiệm cố định.
 *
 * Duplicate có chủ đích với bản Python {@code data-platform/common/level_normalizer.py} —
 * khác với tech alias (dp_tech_alias_map, danh sách mở cần admin sửa), đây là 1 tập bucket
 * cố định nhỏ nên không cần bảng DB riêng; 2 bản phải giữ cùng thứ tự/keyword khi sửa.
 */
public final class LevelNormalizer {

    private LevelNormalizer() {}

    // Thứ tự ưu tiên: cấp cao nhất khớp trước, để "Senior Team Lead" -> Lead, không lẫn Senior.
    private static final Map<String, List<String>> LEVEL_KEYWORDS = Map.ofEntries(
            Map.entry("Lead", List.of("lead", "trưởng nhóm", "team lead", "quản lý", "manager",
                    "trưởng phòng", "phó phòng", "giám đốc", "director", "head of")),
            Map.entry("Senior", List.of("senior", "chuyên gia", "cấp cao")),
            Map.entry("Middle", List.of("middle", "mid-level", "mid level")),
            Map.entry("Junior", List.of("junior", "nhân viên", "staff")),
            Map.entry("Fresher", List.of("fresher", "mới tốt nghiệp", "mới ra trường", "entry level", "graduate")),
            Map.entry("Intern", List.of("intern", "thực tập"))
    );

    private static final List<String> LEVEL_ORDER =
            List.of("Lead", "Senior", "Middle", "Junior", "Fresher", "Intern");

    /** Trả về 1 trong Intern/Fresher/Junior/Middle/Senior/Lead, hoặc null nếu không khớp bucket nào. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        for (String level : LEVEL_ORDER) {
            for (String keyword : LEVEL_KEYWORDS.get(level)) {
                if (text.contains(keyword)) {
                    return level;
                }
            }
        }
        return null;
    }
}
