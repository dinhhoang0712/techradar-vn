package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.SubscriptionTier;
import com.techpulse.techradar.shared.validation.OneOf;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Register request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @Schema(example = "Nguyễn Văn A")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Schema(example = "user@techradar.vn")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(example = "Passw0rd!", minLength = 8)
    private String password;

    @OneOf(SubscriptionTier.class)
    @Schema(description = "Optional; defaults to the FREE tier if omitted.", example = "FREE")
    private String subscriptionTier;
}
