package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.UserDirectoryRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresFollowRepository}, which
 * implements BOTH {@code FollowRepository} (follow-relationship mutation/counts) and
 * {@code UserDirectoryRepository} (profile lookup/search) after that fat interface was split —
 * this test covers every method of both.
 */
@ExtendWith(MockitoExtension.class)
class PostgresFollowRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<Boolean> booleanRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private RowsFetchSpec<UserDirectoryRepository.ProfileBasics> profileRowsFetchSpec;
    @Mock
    private RowsFetchSpec<UserDirectoryRepository.UserSummaryRow> summaryRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresFollowRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresFollowRepository(dbClient);
    }

    @Test
    void follow_returnsTrueWhenNewlyRecorded() {
        UUID follower = UUID.randomUUID();
        UUID followee = UUID.randomUUID();
        when(dbClient.sql(
                "INSERT INTO follow (follower_id, followee_id) VALUES (:follower_id, :followee_id) " +
                        "ON CONFLICT (follower_id, followee_id) DO NOTHING"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("follower_id", follower)).thenReturn(executeSpec);
        when(executeSpec.bind("followee_id", followee)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.follow(follower, followee)).expectNext(true).verifyComplete();
    }

    @Test
    void follow_returnsFalseWhenAlreadyFollowing() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.follow(UUID.randomUUID(), UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void unfollow_bindsBothIds_andCompletes() {
        UUID follower = UUID.randomUUID();
        UUID followee = UUID.randomUUID();
        when(dbClient.sql("DELETE FROM follow WHERE follower_id = :follower_id AND followee_id = :followee_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("follower_id", follower)).thenReturn(executeSpec);
        when(executeSpec.bind("followee_id", followee)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.unfollow(follower, followee)).verifyComplete();

        verify(executeSpec).bind("follower_id", follower);
        verify(executeSpec).bind("followee_id", followee);
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_returnsTrueWhenExists() {
        UUID follower = UUID.randomUUID();
        UUID followee = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT EXISTS(SELECT 1 FROM follow WHERE follower_id = :follower_id AND followee_id = :followee_id) AS following"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.just(true));

        StepVerifier.create(repository.isFollowing(follower, followee)).expectNext(true).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_defaultsToFalseWhenEmpty() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.isFollowing(UUID.randomUUID(), UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_rowMapper_treatsNullAsFalse() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());
        when(row.get("following", Boolean.class)).thenReturn(null);

        repository.isFollowing(UUID.randomUUID(), UUID.randomUUID()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, Boolean>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        assertThat(captor.getValue().apply(row, rowMetadata)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void followerCount_bindsUserId_andUsesFolloweeIdColumn() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql("SELECT count(*) AS c FROM follow WHERE followee_id = :user_id")).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(7L));

        StepVerifier.create(repository.followerCount(userId)).expectNext(7L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void followingCount_bindsUserId_andUsesFollowerIdColumn() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql("SELECT count(*) AS c FROM follow WHERE follower_id = :user_id")).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(3L));

        StepVerifier.create(repository.followingCount(userId)).expectNext(3L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_returnsMappedCount_defaultsToZeroWhenEmpty() {
        when(dbClient.sql("SELECT count(*) AS c FROM follow")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countAll()).expectNext(0L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findProfileBasics_bindsUserId_mapsAllFiveColumns() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT u.full_name, up.avatar_url, up.bio, up.job_role, up.location " +
                        "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                        "WHERE u.id = :user_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(profileRowsFetchSpec);
        when(profileRowsFetchSpec.one()).thenReturn(Mono.empty());
        when(row.get("full_name", String.class)).thenReturn("Alice");
        when(row.get("avatar_url", String.class)).thenReturn("avatar.png");
        when(row.get("bio", String.class)).thenReturn("Software engineer");
        when(row.get("job_role", String.class)).thenReturn("Backend Dev");
        when(row.get("location", String.class)).thenReturn("Hanoi");

        repository.findProfileBasics(userId).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, UserDirectoryRepository.ProfileBasics>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        UserDirectoryRepository.ProfileBasics mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.fullName()).isEqualTo("Alice");
        assertThat(mapped.avatarUrl()).isEqualTo("avatar.png");
        assertThat(mapped.bio()).isEqualTo("Software engineer");
        assertThat(mapped.jobRole()).isEqualTo("Backend Dev");
        assertThat(mapped.location()).isEqualTo("Hanoi");
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggested_bindsViewerIdAndLimit_mapsRows() {
        UUID viewerId = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT u.id, u.full_name, up.avatar_url, " +
                        "       (SELECT count(*) FROM follow f2 WHERE f2.followee_id = u.id) AS follower_count " +
                        "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                        "WHERE u.id <> :viewer_id " +
                        "  AND u.id NOT IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id) " +
                        "ORDER BY follower_count DESC, u.created_at DESC " +
                        "LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("viewer_id", viewerId)).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 5)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(summaryRowsFetchSpec);
        UserDirectoryRepository.UserSummaryRow summary =
                new UserDirectoryRepository.UserSummaryRow(UUID.randomUUID(), "Bob", "bob.png");
        when(summaryRowsFetchSpec.all()).thenReturn(Flux.just(summary));

        StepVerifier.create(repository.suggested(viewerId, 5))
                .expectNext(summary)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByName_escapesLikeMetacharacters_andWrapsInPercent() {
        UUID viewerId = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT u.id, u.full_name, up.avatar_url " +
                        "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                        "WHERE u.id <> :viewer_id AND u.full_name ILIKE :pattern ESCAPE '\\' " +
                        "ORDER BY u.full_name LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(summaryRowsFetchSpec);
        when(summaryRowsFetchSpec.all()).thenReturn(Flux.empty());

        repository.searchByName(viewerId, "100%_done\\path", 20).subscribe();

        verify(executeSpec).bind("viewer_id", viewerId);
        verify(executeSpec).bind("pattern", "%100\\%\\_done\\\\path%");
        verify(executeSpec).bind("limit", 20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByName_mapsRows() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(summaryRowsFetchSpec);
        UserDirectoryRepository.UserSummaryRow summary =
                new UserDirectoryRepository.UserSummaryRow(UUID.randomUUID(), "Carol", null);
        when(summaryRowsFetchSpec.all()).thenReturn(Flux.just(summary));

        StepVerifier.create(repository.searchByName(UUID.randomUUID(), "car", 20))
                .expectNext(summary)
                .verifyComplete();
    }
}
