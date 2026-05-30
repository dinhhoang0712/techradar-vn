package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.user.application.UserService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin user management controller.
 */
@Tag(name = "Admin", description = "Admin user management endpoints")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "List all users")
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<UserProfileResponse>>>> listUsers() {
        return userService.listUsers()
                .map(UserProfileResponse::fromUser)
                .collectList()
                .map(users -> ResponseEntity.ok(ApiResponse.success(users, "Users listed")));
    }

    @Operation(summary = "Create a new user")
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> insertUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        return userService.createUser(
                        request.getEmail(),
                        request.getPassword(),
                        request.getRole(),
                        request.getStatus(),
                        request.getSubscriptionTier())
                .map(UserProfileResponse::fromUser)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(user, "User created")));
    }

    @Operation(summary = "Update an existing user")
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> alterUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.alterUser(
                        id,
                        request.getEmail(),
                        request.getPassword(),
                        request.getRole(),
                        request.getStatus(),
                        request.getSubscriptionTier())
                .map(UserProfileResponse::fromUser)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, "User updated")));
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteUser(
            @PathVariable String id
    ) {
        return userService.deleteUser(id)
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body(ApiResponse.success(null, "User deleted"))));
    }
}
