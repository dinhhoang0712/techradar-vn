package com.techpulse.techradar.shared.exception;

/**
 * Generic 400 for request-level validation failures (bad input, invalid state transition) that
 * don't warrant their own exception class. The {@link ErrorCode} passed in still identifies the
 * specific failure for clients — this class only fixes the HTTP status.
 */
public class BadRequestException extends AppException {
    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
