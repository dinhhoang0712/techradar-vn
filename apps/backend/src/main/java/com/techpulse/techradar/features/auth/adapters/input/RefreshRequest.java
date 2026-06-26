package com.techpulse.techradar.features.auth.adapters.input;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for refreshing an access token.
 * The client sends {@code {"refresh_token": "..."}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "refresh_token is required")
    private String refreshToken;
}
