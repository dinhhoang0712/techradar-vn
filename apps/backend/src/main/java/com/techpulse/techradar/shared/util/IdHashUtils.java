package com.techpulse.techradar.shared.util;

import java.nio.charset.StandardCharsets;
import org.springframework.util.DigestUtils;

/**
 * Helpers for deriving stable, deterministic ids/keys from free-form strings.
 * <p>
 * {@code Neo4jExtractionWriter} and the Kafka article/job extractor services independently
 * re-implemented this MD5-of-UTF8-bytes idiom (used both as the Neo4j node id and as the Kafka
 * message key for a source URL); this is the single shared implementation they should all
 * delegate to.
 */
public final class IdHashUtils {

    private IdHashUtils() {
    }

    /**
     * @return the hex-encoded MD5 digest of {@code value}'s UTF-8 bytes, or of the empty string
     * when {@code value} is {@code null}.
     */
    public static String md5(String value) {
        if (value == null) {
            value = "";
        }
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
