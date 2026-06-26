package com.techpulse.techradar.features.user.adapters.output;

import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL adapter for the {@code user_profile} table (technologies stored as {@code text[]}).
 */
@Repository
@RequiredArgsConstructor
public class PostgresUserProfileRepository implements UserProfileRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<UserProfile> findByUserId(String userId) {
        return dbClient.sql(
                "SELECT user_id, job_role, technologies, location, bio, avatar_url, notify_inapp, notify_email " +
                "FROM user_profile WHERE user_id = :user_id"
        )
                .bind("user_id", UUID.fromString(userId))
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<UserProfile> upsert(UserProfile profile) {
        String[] technologies = profile.getTechnologies() == null
                ? new String[0]
                : profile.getTechnologies().toArray(new String[0]);

        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO user_profile (user_id, job_role, technologies, location, bio, avatar_url, notify_inapp, notify_email, updated_at) " +
                "VALUES (:user_id, :job_role, :technologies, :location, :bio, :avatar_url, :notify_inapp, :notify_email, :updated_at) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "job_role = EXCLUDED.job_role, technologies = EXCLUDED.technologies, location = EXCLUDED.location, " +
                "bio = EXCLUDED.bio, avatar_url = EXCLUDED.avatar_url, " +
                "notify_inapp = EXCLUDED.notify_inapp, notify_email = EXCLUDED.notify_email, updated_at = EXCLUDED.updated_at"
        )
                .bind("user_id", profile.getUserId())
                .bind("technologies", technologies)
                .bind("notify_inapp", profile.getNotifyInapp() != null ? profile.getNotifyInapp() : Boolean.TRUE)
                .bind("notify_email", profile.getNotifyEmail() != null ? profile.getNotifyEmail() : Boolean.TRUE)
                .bind("updated_at", LocalDateTime.now());
        spec = bindNullable(spec, "job_role", profile.getJobRole());
        spec = bindNullable(spec, "location", profile.getLocation());
        spec = bindNullable(spec, "bio", profile.getBio());
        spec = bindNullable(spec, "avatar_url", profile.getAvatarUrl());

        return spec.fetch().rowsUpdated().thenReturn(profile);
    }

    private UserProfile mapRow(Row row) {
        String[] tech = row.get("technologies", String[].class);
        List<String> technologies = tech == null ? List.of() : Arrays.asList(tech);
        return UserProfile.builder()
                .userId(row.get("user_id", UUID.class))
                .jobRole(row.get("job_role", String.class))
                .technologies(technologies)
                .location(row.get("location", String.class))
                .bio(row.get("bio", String.class))
                .avatarUrl(row.get("avatar_url", String.class))
                .notifyInapp(row.get("notify_inapp", Boolean.class))
                .notifyEmail(row.get("notify_email", Boolean.class))
                .build();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, String.class);
    }
}
