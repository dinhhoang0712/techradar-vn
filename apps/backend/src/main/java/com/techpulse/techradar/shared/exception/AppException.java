package com.techpulse.techradar.shared.exception;

/**
 * Base exception for the application.
 * All domain and application exceptions inherit from this.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return errorCode.getStatus().value();
    }

    public String getErrorCode() {
        return errorCode.name();
    }
}
