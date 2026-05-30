package com.techpulse.techradar.shared.exception;

public class DatabaseUnavailableException extends AppException {
    public DatabaseUnavailableException(String message) {
        super(message, 503, "SERVICE_UNAVAILABLE");
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause, 503, "SERVICE_UNAVAILABLE");
    }
}
