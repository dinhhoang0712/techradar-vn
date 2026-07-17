package com.techpulse.techradar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for the "7 giờ trước" bug: a naive {@code LocalDateTime} (no zone info) must
 * serialize with an explicit UTC 'Z' suffix, or JS {@code new Date(...)} silently reinterprets it
 * as the browser's local time instead of UTC.
 */
class JacksonConfigTest {

    private ObjectMapper mapperWithCustomizer() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().utcLocalDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void localDateTime_serializesAsUtcInstant_withZSuffix() throws Exception {
        ObjectMapper mapper = mapperWithCustomizer();
        LocalDateTime value = LocalDateTime.of(2026, 7, 17, 8, 35, 0, 123_000_000);

        String json = mapper.writeValueAsString(value);

        // Must be a quoted JSON string ending in Z — a bare "2026-07-17T08:35:00.123" (no offset)
        // is exactly the bug this fixes.
        assertThat(json).startsWith("\"").endsWith("Z\"");

        String isoString = json.substring(1, json.length() - 1);
        Instant parsed = Instant.parse(isoString); // throws DateTimeParseException if the 'Z' is missing
        assertThat(parsed).isEqualTo(value.toInstant(ZoneOffset.UTC));
    }

    @Test
    void localDateTime_withNoFractionalSeconds_stillHasZSuffix() throws Exception {
        ObjectMapper mapper = mapperWithCustomizer();
        LocalDateTime value = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        String json = mapper.writeValueAsString(value);

        assertThat(json).endsWith("Z\"");
        String isoString = json.substring(1, json.length() - 1);
        assertThat(Instant.parse(isoString)).isEqualTo(value.toInstant(ZoneOffset.UTC));
    }
}
