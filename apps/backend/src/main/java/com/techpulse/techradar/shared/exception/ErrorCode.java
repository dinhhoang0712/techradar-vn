package com.techpulse.techradar.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Single source of truth for every application error code and the HTTP status it maps to.
 * Exceptions reference a constant here instead of carrying their own status/code pair, so the
 * same code can't end up mapped to two different statuses in two different throw sites.
 */
public enum ErrorCode {

    // 400 Bad Request
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST),
    INVALID_REASON(HttpStatus.BAD_REQUEST),
    INVALID_FOLLOW(HttpStatus.BAD_REQUEST),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST),
    INVALID_MENTIONS(HttpStatus.BAD_REQUEST),
    INVALID_COMPANY(HttpStatus.BAD_REQUEST),
    INVALID_PARENT(HttpStatus.BAD_REQUEST),
    INVALID_CONVERSATION(HttpStatus.BAD_REQUEST),
    INVALID_NODE_TYPE(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND),

    // 409 Conflict
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PIPELINE_RUNNING(HttpStatus.CONFLICT),

    // 413 Payload Too Large
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    // 429 Too Many Requests
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
