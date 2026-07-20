package com.techpulse.techradar.shared.exception;

public class ConflictException extends AppException {
    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
