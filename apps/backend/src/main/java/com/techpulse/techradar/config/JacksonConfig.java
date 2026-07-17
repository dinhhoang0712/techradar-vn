package com.techpulse.techradar.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * {@code LocalDateTime} fields (post_comment/direct_message/notification {@code created_at}, etc.)
 * hold a naive wall-clock value with no zone info. The JVM runs in UTC (no {@code TZ} override in
 * the container), so that value already IS UTC. Jackson's default jsr310 serializer writes it
 * without an offset suffix (e.g. "2026-07-17T08:35:00.123"); JS {@code new Date(...)} then parses a
 * zone-less date-time string as the BROWSER's local time, not UTC. In Vietnam (UTC+7) that silently
 * shifts every fresh timestamp exactly 7 hours into the "past", so a message sent seconds ago shows
 * as "7 giờ trước". Serializing with an explicit 'Z' suffix (the value already being UTC) fixes this
 * for every {@code LocalDateTime} field across the API in one place.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC)));
            }
        });
    }
}
