package com.techpulse.techradar.shared.exception;

/**
 * Base exception for the application.
 * All domain and application exceptions inherit from this.
 */
public class AppException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public AppException(String message) {
        this(message, 500, "INTERNAL_SERVER_ERROR");
    }

    public AppException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public AppException(String message, Throwable cause, int statusCode, String errorCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
