package com.techpulse.techradar.shared.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidUtilsTest {

    @Test
    void isValid_returnsTrue_forAValidUuid() {
        assertThat(UuidUtils.isValid(UUID.randomUUID().toString())).isTrue();
    }

    @Test
    void isValid_returnsFalse_forNull() {
        assertThat(UuidUtils.isValid(null)).isFalse();
    }

    @Test
    void isValid_returnsFalse_forBlank() {
        assertThat(UuidUtils.isValid("   ")).isFalse();
    }

    @Test
    void isValid_returnsFalse_forEmptyString() {
        assertThat(UuidUtils.isValid("")).isFalse();
    }

    @Test
    void isValid_returnsFalse_forMalformedString() {
        assertThat(UuidUtils.isValid("not-a-uuid")).isFalse();
    }
}
