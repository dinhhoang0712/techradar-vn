package com.techpulse.techradar.shared.exception;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException(String message) {
        super(message, 401, "INVALID_CREDENTIALS");
    }
}
