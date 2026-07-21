package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresPostRepository implements PostRepository {

    private final DatabaseClient dbClient;

    static final String SELECT_FEED_ROW =
            "SELECT p.id, p.user_id, u.full_name, up.avatar_url, p.content, p.created_at, " +
            "       (SELECT count(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, " +
            "       (SELECT count(*) FROM post_comment pc WHERE pc.post_id = p.id) AS comment_count, " +
            "       EXISTS(SELECT 1 FROM post_like pl2 WHERE pl2.post_id = p.id AND pl2.user_id = :viewer_id) AS liked_by_me, " +
            "       (SELECT array_agg(pi.id ORDER BY pi.ordinal) FROM post_image pi WHERE pi.post_id = p.id) AS image_ids, " +
            "       p.hashtags, p.tagged_company_id, p.tagged_company_name, p.tagged_company_location " +
            "FROM post p " +
            "JOIN users u ON u.id = p.user_id " +
            "LEFT JOIN user_profile up ON up.user_id = p.user_id ";

    @Override
    public Mono<Void> insert(NewPost post) {
        String[] hashtags = post.hashtags() == null ? new String[0] : post.hashtags().toArray(new String[0]);
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO post (id, user_id, content, hashtags, tagged_company_id, tagged_company_name, tagged_company_location, created_at) " +
                "VALUES (:id, :user_id, :content, :hashtags, :tagged_company_id, :tagged_company_name, :tagged_company_location, :created_at)")
                .bind("id", post.id())
                .bind("user_id", post.userId())
                .bind("content", post.content())
                .bind("hashtags", hashtags)
                .bind("created_at", post.createdAt());
        spec = bindNullable(spec, "tagged_company_id", post.taggedCompanyId());
        spec = bindNullable(spec, "tagged_company_name", post.taggedCompanyName());
        spec = bindNullable(spec, "tagged_company_location", post.taggedCompanyLocation());
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Boolean> deleteOwnedBy(UUID postId, UUID userId) {
        return dbClient.sql("DELETE FROM post WHERE id = :id AND user_id = :user_id")
                .bind("id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<FeedRow> findFeed(UUID viewerId, String hashtagFilter, int limit, int offset) {
        String sql = SELECT_FEED_ROW +
                "WHERE (p.user_id = :viewer_id OR p.user_id IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id)) " +
                (hashtagFilter != null ? "AND p.hashtags @> :hashtag_arr " : "") +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(sql)
                .bind("viewer_id", viewerId)
                .bind("limit", limit)
                .bind("offset", offset);
        if (hashtagFilter != null) {
            spec = spec.bind("hashtag_arr", new String[]{hashtagFilter});
        }
        return spec.map((row, meta) -> mapRow(row)).all();
    }

    @Override
    public Flux<FeedRow> findExplore(UUID viewerId, String hashtagFilter, int limit, int offset) {
        String sql = SELECT_FEED_ROW +
                (hashtagFilter != null ? "WHERE p.hashtags @> :hashtag_arr " : "") +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(sql)
                .bind("viewer_id", viewerId)
                .bind("limit", limit)
                .bind("offset", offset);
        if (hashtagFilter != null) {
            spec = spec.bind("hashtag_arr", new String[]{hashtagFilter});
        }
        return spec.map((row, meta) -> mapRow(row)).all();
    }

    @Override
    public Flux<FeedRow> findByUser(UUID targetUserId, UUID viewerId, int limit, int offset) {
        return dbClient.sql(
                SELECT_FEED_ROW +
                "WHERE p.user_id = :target_user_id " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
                .bind("viewer_id", viewerId)
                .bind("target_user_id", targetUserId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<FeedRow> findById(UUID postId, UUID viewerId) {
        return dbClient.sql(SELECT_FEED_ROW + "WHERE p.id = :post_id")
                .bind("viewer_id", viewerId)
                .bind("post_id", postId)
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<Long> countByUser(UUID userId) {
        return dbClient.sql("SELECT count(*) AS c FROM post WHERE user_id = :user_id")
                .bind("user_id", userId)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<UUID> findAuthorId(UUID postId) {
        return dbClient.sql("SELECT user_id FROM post WHERE id = :id")
                .bind("id", postId)
                .map((row, meta) -> row.get("user_id", UUID.class))
                .one();
    }

    @Override
    public Mono<Boolean> like(UUID postId, UUID userId) {
        return dbClient.sql(
                "INSERT INTO post_like (post_id, user_id) VALUES (:post_id, :user_id) " +
                "ON CONFLICT (post_id, user_id) DO NOTHING")
                .bind("post_id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Void> unlike(UUID postId, UUID userId) {
        return dbClient.sql("DELETE FROM post_like WHERE post_id = :post_id AND user_id = :user_id")
                .bind("post_id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated().then();
    }

    static FeedRow mapRow(Row row) {
        UUID[] imageIds = row.get("image_ids", UUID[].class);
        String[] hashtags = row.get("hashtags", String[].class);
        return new FeedRow(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("full_name", String.class),
                row.get("avatar_url", String.class),
                row.get("content", String.class),
                row.get("created_at", java.time.LocalDateTime.class),
                row.get("like_count", Long.class),
                row.get("comment_count", Long.class),
                Boolean.TRUE.equals(row.get("liked_by_me", Boolean.class)),
                imageIds == null ? List.of() : Arrays.asList(imageIds),
                hashtags == null ? List.of() : Arrays.asList(hashtags),
                row.get("tagged_company_id", String.class),
                row.get("tagged_company_name", String.class),
                row.get("tagged_company_location", String.class)
        );
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, String.class);
    }
}
