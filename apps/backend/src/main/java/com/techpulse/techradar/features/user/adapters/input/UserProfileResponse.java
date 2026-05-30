package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.auth.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User profile response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String role;
    private String status;
    private String subscriptionTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfileResponse fromUser(User user) {
        return UserProfileResponse.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .subscriptionTier(user.getSubscriptionTier())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
