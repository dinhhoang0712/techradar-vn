package com.techpulse.techradar.shared.exception;

public class RateLimitExceededException extends AppException {
    public RateLimitExceededException(String message) {
        super(message, 429, "RATE_LIMIT_EXCEEDED");
    }
}