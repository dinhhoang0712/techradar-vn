package com.techpulse.techradar.features.auth.adapters.input;

import com.techpulse.techradar.features.auth.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for {@code GET /auth/me}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private String id;
    private String email;
    private String role;
    private String status;
    private String subscriptionTier;

    public static MeResponse fromUser(User user) {
        return MeResponse.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .subscriptionTier(user.getSubscriptionTier())
                .build();
    }
}
