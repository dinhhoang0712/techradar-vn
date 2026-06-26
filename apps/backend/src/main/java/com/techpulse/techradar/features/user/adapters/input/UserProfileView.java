package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.user.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full user profile returned by {@code GET/PUT /user/profile}.
 * Serialized snake_case: full_name, subscription_tier, avatar_url, job_role, technologies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileView {
    private String id;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private String subscriptionTier;
    private String avatarUrl;
    private String bio;
    private String jobRole;
    private String location;
    private List<String> technologies;
    private Boolean notifyInapp;
    private Boolean notifyEmail;

    public static UserProfileView from(User user, UserProfile profile) {
        UserProfileView.UserProfileViewBuilder b = UserProfileView.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .subscriptionTier(user.getSubscriptionTier());
        if (profile != null) {
            b.avatarUrl(profile.getAvatarUrl())
                    .bio(profile.getBio())
                    .jobRole(profile.getJobRole())
                    .location(profile.getLocation())
                    .technologies(profile.getTechnologies())
                    .notifyInapp(profile.getNotifyInapp() == null || profile.getNotifyInapp())
                    .notifyEmail(profile.getNotifyEmail() == null || profile.getNotifyEmail());
        } else {
            b.technologies(List.of()).notifyInapp(true).notifyEmail(true);
        }
        return b.build();
    }
}
