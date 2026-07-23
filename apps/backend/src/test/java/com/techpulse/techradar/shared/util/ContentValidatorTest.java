package com.techpulse.techradar.shared.util;

import org.junit.jupiter.api.Test;

import com.techpulse.techradar.shared.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentValidatorTest {

    @Test
    void requireValidLength_returnsTrimmedContent() {
        assertThat(ContentValidator.requireValidLength("  hello  ", 100, "Post content")).isEqualTo("hello");
    }

    @Test
    void requireValidLength_rejectsNull() {
        assertThatThrownBy(() -> ContentValidator.requireValidLength(null, 100, "Post content"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void requireValidLength_rejectsBlank() {
        assertThatThrownBy(() -> ContentValidator.requireValidLength("   ", 100, "Post content"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void requireValidLength_rejectsContentLongerThanMax() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> ContentValidator.requireValidLength(tooLong, 100, "Comment"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too long (max 100 chars)");
    }

    @Test
    void requireValidLength_acceptsContentAtExactlyMaxLength() {
        String maxLength = "a".repeat(100);

        assertThat(ContentValidator.requireValidLength(maxLength, 100, "Comment")).hasSize(100);
    }

    @Test
    void requireValidLength_countsLengthAfterTrimming() {
        String withPadding = "  " + "a".repeat(100) + "  ";

        assertThat(ContentValidator.requireValidLength(withPadding, 100, "Comment")).hasSize(100);
    }
}
