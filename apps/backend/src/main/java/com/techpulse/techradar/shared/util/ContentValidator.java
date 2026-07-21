package com.techpulse.techradar.shared.util;

import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;

/**
 * Trims and enforces a max length on user-submitted content. {@code CreatePostUseCase} and
 * {@code AddCommentUseCase} independently re-implemented this exact trim/blank/length check
 * (differing only in the max length and the label used in the message); this is the single shared
 * implementation they should both delegate to. Throws synchronously — a reactive caller should
 * catch the thrown {@link BadRequestException} and turn it into an {@code onError} signal (see
 * {@code PostImageService.validate} / {@code ImageUploadValidator} for the same shape of problem).
 */
public final class ContentValidator {

    private ContentValidator() {
    }

    /**
     * @return the trimmed content.
     * @throws BadRequestException if {@code content} is blank after trimming, or the trimmed
     * content exceeds {@code maxLength} characters.
     */
    public static String requireValidLength(String content, int maxLength, String label) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(ErrorCode.INVALID_CONTENT, label + " must not be empty");
        }
        if (trimmed.length() > maxLength) {
            throw new BadRequestException(ErrorCode.INVALID_CONTENT, label + " too long (max " + maxLength + " chars)");
        }
        return trimmed;
    }
}
