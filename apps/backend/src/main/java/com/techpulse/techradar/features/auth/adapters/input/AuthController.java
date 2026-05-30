package com.techpulse.techradar.features.auth.adapters.input;

import com.techpulse.techradar.features.auth.application.LoginUseCase;
import com.techpulse.techradar.features.auth.application.RegisterUseCase;
import com.techpulse.techradar.features.auth.application.RefreshTokenUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
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

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return loginUseCase.execute(request)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success(response, "Login successful")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                ApiResponse.error(ex.getMessage(), "UNAUTHORIZED")
                        )
                ));
    }

    @Operation(summary = "Register new user")
    @PostMapping("/register")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return registerUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(
                        ApiResponse.success(response, "Registration successful")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.CONFLICT).body(
                                ApiResponse.error(ex.getMessage(), "CONFLICT")
                        )
                ));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> refresh(
            @RequestParam String refreshToken
    ) {
        return refreshTokenUseCase.execute(refreshToken)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success(response, "Token refreshed successfully")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                ApiResponse.error(ex.getMessage(), "UNAUTHORIZED")
                        )
                ));
    }

    @Operation(summary = "Get current user info")
    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<Object>>> getCurrentUser() {
        // This will be implemented with Spring Security context
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Feature coming soon")
        ));
    }

}
