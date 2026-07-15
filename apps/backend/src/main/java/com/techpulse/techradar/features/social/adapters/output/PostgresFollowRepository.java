package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresFollowRepository implements FollowRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> follow(UUID followerId, UUID followeeId) {
        return dbClient.sql(
                "INSERT INTO follow (follower_id, followee_id) VALUES (:follower_id, :followee_id) " +
                "ON CONFLICT (follower_id, followee_id) DO NOTHING")
                .bind("follower_id", followerId)
                .bind("followee_id", followeeId)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> unfollow(UUID followerId, UUID followeeId) {
        return dbClient.sql("DELETE FROM follow WHERE follower_id = :follower_id AND followee_id = :followee_id")
                .bind("follower_id", followerId)
                .bind("followee_id", followeeId)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Boolean> isFollowing(UUID followerId, UUID followeeId) {
        return dbClient.sql(
                "SELECT EXISTS(SELECT 1 FROM follow WHERE follower_id = :follower_id AND followee_id = :followee_id) AS following")
                .bind("follower_id", followerId)
                .bind("followee_id", followeeId)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("following", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> followerCount(UUID userId) {
        return count("SELECT count(*) AS c FROM follow WHERE followee_id = :user_id", userId);
    }

    @Override
    public Mono<Long> followingCount(UUID userId) {
        return count("SELECT count(*) AS c FROM follow WHERE follower_id = :user_id", userId);
    }

    private Mono<Long> count(String sql, UUID userId) {
        return dbClient.sql(sql)
                .bind("user_id", userId)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<ProfileBasics> findProfileBasics(UUID userId) {
        return dbClient.sql(
                "SELECT u.full_name, up.avatar_url, up.bio, up.job_role, up.location " +
                "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                "WHERE u.id = :user_id")
                .bind("user_id", userId)
                .map((row, meta) -> new ProfileBasics(
                        row.get("full_name", String.class),
                        row.get("avatar_url", String.class),
                        row.get("bio", String.class),
                        row.get("job_role", String.class),
                        row.get("location", String.class)
                ))
                .one();
    }

    @Override
    public Flux<UserSummaryRow> suggested(UUID viewerId, int limit) {
        return dbClient.sql(
                "SELECT u.id, u.full_name, up.avatar_url, " +
                "       (SELECT count(*) FROM follow f2 WHERE f2.followee_id = u.id) AS follower_count " +
                "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                "WHERE u.id <> :viewer_id " +
                "  AND u.id NOT IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id) " +
                "ORDER BY follower_count DESC, u.created_at DESC " +
                "LIMIT :limit")
                .bind("viewer_id", viewerId)
                .bind("limit", limit)
                .map((row, meta) -> new UserSummaryRow(
                        row.get("id", UUID.class),
                        row.get("full_name", String.class),
                        row.get("avatar_url", String.class)
                ))
                .all();
    }
}
