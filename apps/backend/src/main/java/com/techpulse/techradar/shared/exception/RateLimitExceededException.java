package com.techpulse.techradar.shared.exception;

public class RateLimitExceededException extends AppException {
    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message);
    }
}