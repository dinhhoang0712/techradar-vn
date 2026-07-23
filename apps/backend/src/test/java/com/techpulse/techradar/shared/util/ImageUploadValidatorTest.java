package com.techpulse.techradar.shared.util;

import org.junit.jupiter.api.Test;

import com.techpulse.techradar.shared.exception.BadRequestException;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTest {

    private static String base64Of(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void validate_decodesValidBase64AndNormalizesContentType() {
        byte[] bytes = {1, 2, 3, 4};

        ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate("IMAGE/PNG", base64Of(bytes));

        assertThat(decoded.contentType()).isEqualTo("image/png");
        assertThat(decoded.data()).isEqualTo(bytes);
    }

    @Test
    void validate_stripsDataUrlPrefixBeforeDecoding() {
        byte[] bytes = {5, 6, 7};
        String dataUrl = "data:image/png;base64," + base64Of(bytes);

        ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate("image/png", dataUrl);

        assertThat(decoded.data()).isEqualTo(bytes);
    }

    @Test
    void validate_defaultsBlankContentTypeToImagePng() {
        ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate("  ", base64Of(new byte[]{1}));

        assertThat(decoded.contentType()).isEqualTo("image/png");
    }

    @Test
    void validate_defaultsNullContentTypeToImagePng() {
        ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate(null, base64Of(new byte[]{1}));

        assertThat(decoded.contentType()).isEqualTo("image/png");
    }

    @Test
    void validate_rejectsInvalidBase64() {
        assertThatThrownBy(() -> ImageUploadValidator.validate("image/png", "not-valid-base64!!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid base64");
    }

    @Test
    void validate_rejectsEmptyDecodedPayload() {
        assertThatThrownBy(() -> ImageUploadValidator.validate("image/png", ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty or too large");
    }

    @Test
    void validate_rejectsPayloadLargerThanMaxBytes() {
        byte[] tooBig = new byte[ImageUploadValidator.MAX_BYTES + 1];

        assertThatThrownBy(() -> ImageUploadValidator.validate("image/png", base64Of(tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty or too large");
    }

    @Test
    void validate_acceptsPayloadAtExactlyMaxBytes() {
        byte[] atLimit = new byte[ImageUploadValidator.MAX_BYTES];

        ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate("image/png", base64Of(atLimit));

        assertThat(decoded.data()).hasSize(ImageUploadValidator.MAX_BYTES);
    }

    @Test
    void validate_rejectsDisallowedContentType_evenWithValidImageBytes() {
        assertThatThrownBy(() -> ImageUploadValidator.validate("image/svg+xml", base64Of(new byte[]{1, 2})))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported image type");
    }

    @Test
    void validate_acceptsAllDocumentedAllowedTypes() {
        for (String type : ImageUploadValidator.ALLOWED_TYPES) {
            ImageUploadValidator.Decoded decoded = ImageUploadValidator.validate(type, base64Of(new byte[]{9}));
            assertThat(decoded.contentType()).isEqualTo(type);
        }
    }
}
