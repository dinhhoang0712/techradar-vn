package com.techpulse.techradar.shared.util;

import com.techpulse.techradar.shared.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadValidatorTest {

    private static String base64Of(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void validate_throwsForInvalidBase64() {
        assertThatThrownBy(() -> FileUploadValidator.validate("image/png", "not-valid-base64!!", "a.png"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_ATTACHMENT"));
    }

    @Test
    void validate_throwsForEmptyDecodedData() {
        assertThatThrownBy(() -> FileUploadValidator.validate("image/png", "", "a.png"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void validate_throwsForOversizedFile() {
        byte[] tooBig = new byte[FileUploadValidator.MAX_BYTES + 1];
        assertThatThrownBy(() -> FileUploadValidator.validate("image/png", base64Of(tooBig), "a.png"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_ATTACHMENT"));
    }

    @Test
    void validate_acceptsAFileAtExactlyTheSizeLimit() {
        byte[] atLimit = new byte[FileUploadValidator.MAX_BYTES];
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate("image/png", base64Of(atLimit), "a.png");
        assertThat(decoded.data()).hasSize(FileUploadValidator.MAX_BYTES);
    }

    @Test
    void validate_throwsForUnsupportedContentType() {
        assertThatThrownBy(() -> FileUploadValidator.validate("image/svg+xml", base64Of("<svg/>".getBytes()), "a.svg"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_ATTACHMENT"));
    }

    @Test
    void validate_rejectsExecutableContentType() {
        assertThatThrownBy(() -> FileUploadValidator.validate("application/x-msdownload", base64Of("MZ".getBytes()), "virus.exe"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void validate_acceptsEveryDocumentedAllowedType() {
        for (String type : FileUploadValidator.ALLOWED_TYPES) {
            FileUploadValidator.Decoded decoded = FileUploadValidator.validate(type, base64Of("x".getBytes()), "a.file");
            assertThat(decoded.contentType()).isEqualTo(type);
        }
    }

    @Test
    void validate_rejectsABlankContentTypeSinceItNormalizesToAnUnlistedType() {
        // Blank normalizes to "application/octet-stream", which is deliberately NOT in
        // ALLOWED_TYPES — unlike ImageUploadValidator, a missing content type here is treated as
        // "unknown" (rejected) rather than defaulted to an accepted type.
        assertThatThrownBy(() -> FileUploadValidator.validate("", base64Of("x".getBytes()), "a.file"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_ATTACHMENT"));
    }

    @Test
    void validate_stripsDataUrlPrefixBeforeDecoding() {
        String dataUrl = "data:image/png;base64," + base64Of("hello".getBytes());
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate("image/png", dataUrl, "a.png");
        assertThat(decoded.data()).isEqualTo("hello".getBytes());
    }

    @Test
    void validate_sanitizesFilenameByStrippingUnsafeCharacters() {
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate(
                "image/png", base64Of("x".getBytes()), "evil\"; rm -rf /\r\n.png");
        assertThat(decoded.filename()).doesNotContain("\"", "\r", "\n");
    }

    @Test
    void validate_defaultsFilenameWhenBlank() {
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate("image/png", base64Of("x".getBytes()), "  ");
        assertThat(decoded.filename()).isEqualTo("file");
    }

    @Test
    void validate_defaultsFilenameWhenNull() {
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate("image/png", base64Of("x".getBytes()), null);
        assertThat(decoded.filename()).isEqualTo("file");
    }

    @Test
    void validate_truncatesFilenamesLongerThan255Characters() {
        String longName = "a".repeat(300) + ".png";
        FileUploadValidator.Decoded decoded = FileUploadValidator.validate("image/png", base64Of("x".getBytes()), longName);
        assertThat(decoded.filename()).hasSize(255);
    }
}
