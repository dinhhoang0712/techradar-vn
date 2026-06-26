package com.techpulse.techradar.shared.exception;

public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super(message, 403, "FORBIDDEN");
    }
}
