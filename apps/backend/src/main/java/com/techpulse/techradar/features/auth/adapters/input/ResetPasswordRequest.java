package com.techpulse.techradar.features.auth.adapters.input;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code {"token": "...", "new_password": "..."}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "new_password is required")
    private String newPassword;
}
