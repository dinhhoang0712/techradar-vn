package com.techpulse.techradar.shared.exception;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
