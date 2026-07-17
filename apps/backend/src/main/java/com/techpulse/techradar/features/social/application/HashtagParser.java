package com.techpulse.techradar.features.social.application;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code #hashtag} tokens from post/comment content. Content itself is stored verbatim —
 * this only produces the derived {@code post.hashtags} index used for filtering/trending.
 */
final class HashtagParser {

    private static final int MAX_HASHTAGS = 20;
    // Must start with a letter (rejects bare "#123"/"#_x"); \p{L}/\p{N} are Unicode-aware in Java,
    // so this matches Vietnamese diacritic letters (ô, ệ, ữ, …) without extra flags.
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}][\\p{L}\\p{N}_]{0,49})");

    private HashtagParser() {
    }

    static List<String> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        // Normalize to NFC first: some input paths yield NFD-decomposed Vietnamese diacritics,
        // where a combining mark can otherwise split off from its base letter mid-match.
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFC);
        Matcher matcher = HASHTAG_PATTERN.matcher(normalized);

        Set<String> tags = new LinkedHashSet<>();
        while (matcher.find() && tags.size() < MAX_HASHTAGS) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tags);
    }
}
