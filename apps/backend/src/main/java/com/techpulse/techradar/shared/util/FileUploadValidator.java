package com.techpulse.techradar.shared.util;

import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;

import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decodes and validates a base64-encoded message attachment: strips an optional data-URL prefix,
 * decodes the payload, enforces {@link #MAX_BYTES}, enforces the {@link #ALLOWED_TYPES}
 * content-type allowlist, and sanitizes the filename. Sibling to {@link ImageUploadValidator} —
 * kept separate because message attachments allow a broader set of content types (documents, not
 * just raster images) and a larger size limit.
 */
public final class FileUploadValidator {

    public static final int MAX_BYTES = 10 * 1024 * 1024; // 10 MB
    // No image/svg+xml (stored-XSS risk on the public serve endpoint) and no executable/script types.
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "application/zip");

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\r\\n\"\\\\/]");
    private static final String DEFAULT_FILENAME = "file";

    private FileUploadValidator() {
    }

    /**
     * @throws BadRequestException if {@code dataBase64} isn't valid base64, decodes to an empty or
     * over-{@link #MAX_BYTES} payload, or {@code contentType} isn't in {@link #ALLOWED_TYPES}.
     */
    public static Decoded validate(String contentType, String dataBase64, String filename) {
        byte[] data = decode(dataBase64);
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new BadRequestException(ErrorCode.INVALID_ATTACHMENT, "File empty or too large (max 10MB)");
        }

        String ct = normalizeContentType(contentType);
        if (!ALLOWED_TYPES.contains(ct)) {
            throw new BadRequestException(ErrorCode.INVALID_ATTACHMENT, "Unsupported file type");
        }
        return new Decoded(ct, sanitizeFilename(filename), data);
    }

    private static byte[] decode(String dataBase64) {
        String base64 = dataBase64 == null ? "" : dataBase64;
        int comma = base64.indexOf(','); // strip data URL prefix if present
        if (comma >= 0) {
            base64 = base64.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorCode.INVALID_ATTACHMENT, "Invalid base64 file");
        }
    }

    private static String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? "application/octet-stream" : contentType.toLowerCase().trim();
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return DEFAULT_FILENAME;
        }
        String stripped = UNSAFE_FILENAME_CHARS.matcher(filename.trim()).replaceAll("_");
        return stripped.length() > 255 ? stripped.substring(0, 255) : stripped;
    }

    /** Decoded attachment bytes together with their normalized content type and sanitized filename. */
    public record Decoded(String contentType, String filename, byte[] data) {
    }
}
