package com.techpulse.techradar.shared.exception;

public class DatabaseUnavailableException extends AppException {
    public DatabaseUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
