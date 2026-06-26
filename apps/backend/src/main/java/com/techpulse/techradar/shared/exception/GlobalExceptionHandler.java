package com.techpulse.techradar.shared.exception;

import com.techpulse.techradar.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link AppException}s thrown from controllers / use cases to their declared HTTP status and
 * the {@link ApiResponse} error envelope, so e.g. {@link ForbiddenException} becomes 403 instead of 500.
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
}
