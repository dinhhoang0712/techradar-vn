package com.techpulse.techradar.shared.util;

import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;

import java.util.Base64;
import java.util.Set;

/**
 * Decodes and validates a base64-encoded image upload: strips an optional data-URL prefix,
 * decodes the payload, enforces {@link #MAX_BYTES}, and enforces the {@link #ALLOWED_TYPES}
 * content-type allowlist.
 * <p>
 * {@code AvatarService} and {@code PostImageService} independently re-implemented this exact
 * validation (down to the same SVG-XSS rationale comment below); this is the single shared
 * implementation they should both delegate to. Throws synchronously — a reactive caller should
 * run it inside {@code Mono.fromCallable(...)} so the thrown {@link BadRequestException} becomes
 * an {@code onError} signal instead of escaping the assembly call.
 */
public final class ImageUploadValidator {

    public static final int MAX_BYTES = 3 * 1024 * 1024; // 3 MB
    // Raster-only allowlist: no image/svg+xml (SVG can carry script -> stored XSS on the public serve endpoint).
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    private ImageUploadValidator() {
    }

    /**
     * @throws BadRequestException if {@code dataBase64} isn't valid base64, decodes to an empty or
     * over-{@link #MAX_BYTES} payload, or {@code contentType} isn't in {@link #ALLOWED_TYPES}.
     */
    public static Decoded validate(String contentType, String dataBase64) {
        byte[] data = decode(dataBase64);
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Image empty or too large (max 3MB)");
        }

        String ct = normalizeContentType(contentType);
        if (!ALLOWED_TYPES.contains(ct)) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Unsupported image type (png/jpeg/webp/gif only)");
        }
        return new Decoded(ct, data);
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
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Invalid base64 image");
        }
    }

    private static String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? "image/png" : contentType.toLowerCase().trim();
    }

    /** Decoded image bytes together with their normalized (validated) content type. */
    public record Decoded(String contentType, byte[] data) {
    }
}
