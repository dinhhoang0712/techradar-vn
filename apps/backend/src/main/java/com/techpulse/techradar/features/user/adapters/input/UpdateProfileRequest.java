package com.techpulse.techradar.features.user.adapters.input;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for profile updates. All fields optional; only provided ones are applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private String fullName;

    @Email(message = "Email should be valid")
    private String email;

    private String password;
    private String subscriptionTier;
    private String jobRole;
    private String bio;
    private String location;
    private String avatarUrl;
    private List<String> technologies;
    private Boolean notifyInapp;
    private Boolean notifyEmail;
}
