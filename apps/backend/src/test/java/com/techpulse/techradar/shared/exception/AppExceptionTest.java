package com.techpulse.techradar.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every exception subclass must resolve its HTTP status/errorCode purely from {@link ErrorCode} —
 * these tests pin that mapping so a future edit to one class can't silently change its status.
 */
class AppExceptionTest {

    @Test
    void notFoundException_alwaysMapsTo404() {
        NotFoundException ex = new NotFoundException("Post not found");
        assertThat(ex.getStatusCode()).isEqualTo(404);
        assertThat(ex.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Post not found");
    }

    @Test
    void forbiddenException_alwaysMapsTo403() {
        ForbiddenException ex = new ForbiddenException("No access");
        assertThat(ex.getStatusCode()).isEqualTo(403);
        assertThat(ex.getErrorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void invalidCredentialsException_alwaysMapsTo401() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Bad login");
        assertThat(ex.getStatusCode()).isEqualTo(401);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void rateLimitExceededException_alwaysMapsTo429() {
        RateLimitExceededException ex = new RateLimitExceededException("Slow down");
        assertThat(ex.getStatusCode()).isEqualTo(429);
        assertThat(ex.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void databaseUnavailableException_alwaysMapsTo503_andPreservesCause() {
        RuntimeException cause = new RuntimeException("connect timeout");
        DatabaseUnavailableException ex = new DatabaseUnavailableException("RAG service unavailable", cause);
        assertThat(ex.getStatusCode()).isEqualTo(503);
        assertThat(ex.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void badRequestException_statusComesFromTheGivenErrorCode() {
        BadRequestException ex = new BadRequestException(ErrorCode.INVALID_IMAGE, "Unsupported type");
        assertThat(ex.getStatusCode()).isEqualTo(400);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_IMAGE");
    }

    @Test
    void conflictException_statusComesFromTheGivenErrorCode() {
        ConflictException ex = new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email taken");
        assertThat(ex.getStatusCode()).isEqualTo(409);
        assertThat(ex.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void everyErrorCode_hasAnHttpStatusAssigned() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getStatus()).isNotNull();
        }
    }
}
