package com.techpulse.techradar.shared.exception;

import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.dto.ApiResponse.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;

/**
 * Maps every exception that can escape a controller / use case to the {@link ApiResponse} error
 * envelope, so e.g. {@link ForbiddenException} becomes 403 instead of 500 and nothing falls back
 * to Spring Boot's default (differently-shaped) error body.
 * <p>
 * Handlers are ordered from most to least specific; the {@link #handleUnexpected} catch-all at the
 * bottom is the backstop for anything not covered above it (bugs, third-party library exceptions).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        if (ex.getStatusCode() >= 500) {
            log.error("Application error", ex);
        }
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(DataBufferLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyTooLarge(DataBufferLimitException ex) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.getStatus())
                .body(ApiResponse.error("Request body too large", ErrorCode.PAYLOAD_TOO_LARGE.name()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(WebExchangeBindException ex) {
        return validationResponse(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(firstMessageOrDefault(errors), ErrorCode.VALIDATION_ERROR.name(), errors));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedInput(ServerWebInputException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error("Malformed request body or parameters", ErrorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Rejected request with invalid argument: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.error("Invalid request", ErrorCode.BAD_REQUEST.name()));
    }

    // Spring Security's own exceptions for a @PreAuthorize failure (or its programmatic
    // equivalent) — without these, the Exception.class catch-all below would swallow them and
    // report 500 instead of 403 for every authenticated-but-not-authorized request.
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(Exception ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(ApiResponse.error("Access denied", ErrorCode.FORBIDDEN.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error("Internal server error", ErrorCode.INTERNAL_SERVER_ERROR.name()));
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(List<FieldError> fieldErrors) {
        List<FieldErrorDetail> errors = fieldErrors.stream()
                .map(err -> new FieldErrorDetail(
                        err.getField(),
                        err.getDefaultMessage() != null ? err.getDefaultMessage() : "is invalid"))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(firstMessageOrDefault(errors), ErrorCode.VALIDATION_ERROR.name(), errors));
    }

    private String firstMessageOrDefault(List<FieldErrorDetail> errors) {
        return errors.isEmpty() ? "Validation failed" : errors.get(0).field() + ": " + errors.get(0).message();
    }
}
