package com.techpulse.techradar.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic API response wrapper used by every endpoint EXCEPT {@code /auth/login},
 * {@code /auth/register}, {@code /auth/refresh}, {@code /auth/me}, and {@code /status} — those
 * return the success payload bare (no envelope) because the web/mobile clients read fields like
 * {@code access_token}/{@code role} at the top level. That exception is intentional, not a gap;
 * see {@code docs/adr/0003-api-envelope-with-auth-exception.md}.
 */
@Schema(description = "Standard response envelope. success=true responses carry `data`; " +
        "success=false responses carry `message`/`error_code` (and `errors` for per-field " +
        "validation failures) instead. NOT used by /auth/login, /auth/register, /auth/refresh, " +
        "/auth/me, /status success bodies — see ADR-0003.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Schema(description = "false when this is an error response — check this field first.")
    private boolean success;
    @Schema(description = "Present only when success=true.")
    private T data;
    @Schema(description = "Human-readable message — success note or error summary.", example = "OK")
    private String message;
    @Schema(description = "Machine-readable error code, present only when success=false.", example = "VALIDATION_ERROR")
    private String errorCode;
    /** Per-field breakdown for multi-field validation errors; null for every other error. */
    @Schema(description = "Per-field validation errors; present only for multi-field validation failures.")
    private List<FieldErrorDetail> errors;
    @Schema(description = "Server-side epoch millis when this response was built.")
    private long timestamp;

    public record FieldErrorDetail(String field, String message) {
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, List<FieldErrorDetail> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .errors(errors)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
