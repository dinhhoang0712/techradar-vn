package com.techpulse.techradar.features.user.adapters.output;

import com.techpulse.techradar.features.user.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.user.domain.NotificationRecipient;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.db.R2dbcBinders;
import com.techpulse.techradar.shared.util.UuidUtils;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
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
        // A malformed id can never match a row, so treat it the same as a valid-but-unknown one
        // (Mono.empty(), per .one()'s no-rows contract below) instead of letting
        // UUID.fromString blow up synchronously outside the reactive chain.
        if (!UuidUtils.isValid(userId)) {
            return Mono.empty();
        }
        return dbClient.sql(
                "SELECT user_id, job_role, current_level, technologies, location, bio, avatar_url, notify_inapp, notify_email " +
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
                "INSERT INTO user_profile (user_id, job_role, current_level, technologies, location, bio, avatar_url, notify_inapp, notify_email, updated_at) " +
                "VALUES (:user_id, :job_role, :current_level, :technologies, :location, :bio, :avatar_url, :notify_inapp, :notify_email, :updated_at) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "job_role = EXCLUDED.job_role, current_level = EXCLUDED.current_level, technologies = EXCLUDED.technologies, location = EXCLUDED.location, " +
                "bio = EXCLUDED.bio, avatar_url = EXCLUDED.avatar_url, " +
                "notify_inapp = EXCLUDED.notify_inapp, notify_email = EXCLUDED.notify_email, updated_at = EXCLUDED.updated_at"
        )
                .bind("user_id", profile.getUserId())
                .bind("technologies", technologies)
                .bind("notify_inapp", profile.getNotifyInapp() != null ? profile.getNotifyInapp() : Boolean.TRUE)
                .bind("notify_email", profile.getNotifyEmail() != null ? profile.getNotifyEmail() : Boolean.TRUE)
                .bind("updated_at", LocalDateTime.now());
        spec = R2dbcBinders.bindNullable(spec, "job_role", profile.getJobRole());
        spec = R2dbcBinders.bindNullable(spec, "current_level", profile.getCurrentLevel());
        spec = R2dbcBinders.bindNullable(spec, "location", profile.getLocation());
        spec = R2dbcBinders.bindNullable(spec, "bio", profile.getBio());
        spec = R2dbcBinders.bindNullable(spec, "avatar_url", profile.getAvatarUrl());

        return spec.fetch().rowsUpdated().thenReturn(profile);
    }

    @Override
    public Flux<NotificationRecipient> findSubscribersByTechnology(String technology) {
        // @> (contains) instead of `:tech = ANY(technologies)` so the GIN index on
        // user_profile.technologies (V10) can be used instead of a sequential scan.
        return dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email " +
                "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                "WHERE p.technologies @> :tech AND (p.notify_inapp = true OR p.notify_email = true)"
        )
                .bind("tech", new String[] { technology })
                .map((row, meta) -> mapRecipientRow(row))
                .all();
    }

    @Override
    public Flux<JobMatchSubscriber> findJobMatchSubscribers(List<String> technologies) {
        // && (overlap) against both technologies (GIN index, V10) and target_skills (GIN index,
        // V22) so one query covers every skill the job requires, on both "already have" and
        // "learning next" columns. user_profile.user_id is the table's PRIMARY KEY, so the join
        // is already one row per user — no DISTINCT/dedup needed for the two-column OR.
        String[] techs = technologies.toArray(new String[0]);
        return dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email, " +
                "(p.technologies && :techs) AS matches_current " +
                "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                "WHERE (p.technologies && :techs OR p.target_skills && :techs) " +
                "AND (p.notify_inapp = true OR p.notify_email = true)"
        )
                .bind("techs", techs)
                .map((row, meta) -> mapJobMatchSubscriberRow(row))
                .all();
    }

    @Override
    public Flux<NotificationRecipient> findSubscribersWithAnyTechnology() {
        // Same GIN-indexed user_profile table as findSubscribersByTechnology/findJobMatchSubscribers,
        // just without a specific-technology filter — any user with a non-empty tech list is a
        // candidate for the weekly roadmap recompute.
        return dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email " +
                "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                "WHERE p.technologies IS NOT NULL AND array_length(p.technologies, 1) > 0 " +
                "AND (p.notify_inapp = true OR p.notify_email = true)"
        )
                .map((row, meta) -> mapRecipientRow(row))
                .all();
    }

    @Override
    public Mono<Long> updateTargetSkills(String userId, List<String> skills) {
        return dbClient.sql("UPDATE user_profile SET target_skills = :skills WHERE user_id = :user_id")
                .bind("user_id", UUID.fromString(userId))
                .bind("skills", skills.toArray(new String[0]))
                .fetch().rowsUpdated();
    }

    @Override
    public Mono<Long> countWithCurrentLevel() {
        return dbClient.sql("SELECT COUNT(*) AS c FROM user_profile WHERE current_level IS NOT NULL")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    private NotificationRecipient mapRecipientRow(Row row) {
        return new NotificationRecipient(
                row.get("user_id", UUID.class),
                row.get("email", String.class),
                Boolean.TRUE.equals(row.get("notify_inapp", Boolean.class)),
                Boolean.TRUE.equals(row.get("notify_email", Boolean.class)));
    }

    private JobMatchSubscriber mapJobMatchSubscriberRow(Row row) {
        return new JobMatchSubscriber(
                row.get("user_id", UUID.class),
                row.get("email", String.class),
                Boolean.TRUE.equals(row.get("notify_inapp", Boolean.class)),
                Boolean.TRUE.equals(row.get("notify_email", Boolean.class)),
                Boolean.TRUE.equals(row.get("matches_current", Boolean.class)));
    }

    private UserProfile mapRow(Row row) {
        String[] tech = row.get("technologies", String[].class);
        List<String> technologies = tech == null ? List.of() : Arrays.asList(tech);
        return UserProfile.builder()
                .userId(row.get("user_id", UUID.class))
                .jobRole(row.get("job_role", String.class))
                .currentLevel(row.get("current_level", String.class))
                .technologies(technologies)
                .location(row.get("location", String.class))
                .bio(row.get("bio", String.class))
                .avatarUrl(row.get("avatar_url", String.class))
                .notifyInapp(row.get("notify_inapp", Boolean.class))
                .notifyEmail(row.get("notify_email", Boolean.class))
                .build();
    }
}
