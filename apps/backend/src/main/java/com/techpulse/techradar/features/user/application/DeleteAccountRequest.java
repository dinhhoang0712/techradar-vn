package com.techpulse.techradar.features.user.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for GDPR self-service account deletion - requires the current password so a
 * hijacked-but-unexpired session can't be used to destroy the account without knowing it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}
