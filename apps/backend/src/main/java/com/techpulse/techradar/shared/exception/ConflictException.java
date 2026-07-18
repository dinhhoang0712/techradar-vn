package com.techpulse.techradar.shared.exception;

public class ConflictException extends AppException {
    public ConflictException(String message, String errorCode) {
        super(message, 409, errorCode);
    }
}
