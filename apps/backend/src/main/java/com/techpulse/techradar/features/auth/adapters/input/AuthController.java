package com.techpulse.techradar.features.auth.adapters.input;

import com.techpulse.techradar.features.auth.application.ForgotPasswordUseCase;
import com.techpulse.techradar.features.auth.application.GetCurrentUserUseCase;
import com.techpulse.techradar.features.auth.application.LoginUseCase;
import com.techpulse.techradar.features.auth.application.LogoutUseCase;
import com.techpulse.techradar.features.auth.application.RegisterUseCase;
import com.techpulse.techradar.features.auth.application.RefreshTokenUseCase;
import com.techpulse.techradar.features.auth.application.ResetPasswordUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Auth API controller - input adapter for auth module.
 * Handles HTTP requests and delegates to use cases.
 */
@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    // NOTE: auth + /status responses are returned BARE (no ApiResponse envelope) because the
    // web/mobile clients read these fields at the top level (e.g. res.access_token, user.role).
    // Error bodies still use ApiResponse.error so the client can read errData.message.

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return loginUseCase.execute(request)
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                (Object) ApiResponse.error(ex.getMessage(), "UNAUTHORIZED")
                        )
                ));
    }

    @Operation(summary = "Register new user")
    @PostMapping("/register")
    public Mono<ResponseEntity<Object>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return registerUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body((Object) response))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.CONFLICT).body(
                                (Object) ApiResponse.error(ex.getMessage(), "CONFLICT")
                        )
                ));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return refreshTokenUseCase.execute(request.getRefreshToken())
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                (Object) ApiResponse.error(ex.getMessage(), "UNAUTHORIZED")
                        )
                ));
    }

    @Operation(summary = "Log out — blacklists the refresh token so it cannot be reused")
    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        return logoutUseCase.execute(request.getRefreshToken())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Logged out")));
    }

    @Operation(summary = "Request a password-reset token (emailed; always returns 200)")
    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<ApiResponse<Void>>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return forgotPasswordUseCase.execute(request.getEmail())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(
                        null, "If the email exists, a reset link has been sent")));
    }

    @Operation(summary = "Reset password using a valid token")
    @PostMapping("/reset-password")
    public Mono<ResponseEntity<ApiResponse<Void>>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return resetPasswordUseCase.execute(request.getToken(), request.getNewPassword())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Password updated")));
    }

    @Operation(summary = "Get current authenticated user info")
    @GetMapping("/me")
    public Mono<ResponseEntity<Object>> getCurrentUser() {
        return SecurityUtils.currentUserId()
                .flatMap(getCurrentUserUseCase::execute)
                .map(user -> ResponseEntity.ok((Object) MeResponse.fromUser(user)))
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                (Object) ApiResponse.error("Unauthorized", "UNAUTHORIZED")
                        )
                ));
    }

}
