package com.techpulse.techradar.features.auth.domain;

/**
 * User role enumeration.
 */
public enum UserRole {
    USER("user"),
    ADMIN("admin"),
    MODERATOR("moderator");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserRole fromValue(String value) {
        for (UserRole role : UserRole.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return USER;
    }
}
