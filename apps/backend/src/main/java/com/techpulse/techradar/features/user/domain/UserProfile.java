package com.techpulse.techradar.features.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Extended user profile, stored in the {@code user_profile} table shared with the ai-rag-core service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private UUID userId;
    private String jobRole;
    private List<String> technologies;
    private String location;
    private String bio;
    private String avatarUrl;
    private Boolean notifyInapp;
    private Boolean notifyEmail;
}
