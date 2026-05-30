package com.techpulse.techradar.shared.exception;

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(message, 404, "NOT_FOUND");
    }
}
