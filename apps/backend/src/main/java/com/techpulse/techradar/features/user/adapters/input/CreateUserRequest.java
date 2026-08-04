package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.auth.domain.SubscriptionTier;
import com.techpulse.techradar.features.auth.domain.UserStatus;
import com.techpulse.techradar.shared.validation.OneOf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for admin user creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String fullName;
    private String role;

    @OneOf(UserStatus.class)
    private String status;

    @OneOf(SubscriptionTier.class)
    private String subscriptionTier;
}
