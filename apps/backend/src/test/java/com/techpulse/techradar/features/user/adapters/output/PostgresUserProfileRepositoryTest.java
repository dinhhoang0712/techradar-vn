package com.techpulse.techradar.features.user.adapters.output;

import com.techpulse.techradar.features.user.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.user.domain.NotificationRecipient;
import com.techpulse.techradar.features.user.domain.UserProfile;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresUserProfileRepository} —
 * this hand-written SQL/row-mapping code (including the {@code text[]} technologies column) would
 * otherwise silently break on a typo'd column name or wrong bind order.
 */
@ExtendWith(MockitoExtension.class)
class PostgresUserProfileRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<UserProfile> profileRowsFetchSpec;
    @Mock
    private RowsFetchSpec<NotificationRecipient> recipientRowsFetchSpec;
    @Mock
    private RowsFetchSpec<JobMatchSubscriber> jobMatchRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresUserProfileRepository repository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        repository = new PostgresUserProfileRepository(dbClient);
    }

    @Test
    void findByUserId_withMalformedId_returnsEmpty_withoutTouchingTheDatabase() {
        StepVerifier.create(repository.findByUserId("not-a-uuid")).verifyComplete();

        verifyNoInteractions(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByUserId_withValidId_bindsUserIdAndMapsRow() {
        when(dbClient.sql(
                "SELECT user_id, job_role, current_level, technologies, location, bio, avatar_url, notify_inapp, notify_email " +
                        "FROM user_profile WHERE user_id = :user_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", UUID.fromString(USER_ID))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(profileRowsFetchSpec);
        when(profileRowsFetchSpec.one()).thenReturn(Mono.just(
                UserProfile.builder().userId(UUID.fromString(USER_ID)).build()));

        StepVerifier.create(repository.findByUserId(USER_ID))
                .assertNext(p -> assertThat(p.getUserId()).isEqualTo(UUID.fromString(USER_ID)))
                .verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByUserId_rowMapper_withTechnologies_mapsAllColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(profileRowsFetchSpec);
        when(profileRowsFetchSpec.one()).thenReturn(Mono.empty());
        UUID userId = UUID.fromString(USER_ID);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("job_role", String.class)).thenReturn("Engineer");
        when(row.get("current_level", String.class)).thenReturn("Middle");
        when(row.get("technologies", String[].class)).thenReturn(new String[] { "Java", "Kotlin" });
        when(row.get("location", String.class)).thenReturn("Hanoi");
        when(row.get("bio", String.class)).thenReturn("bio text");
        when(row.get("avatar_url", String.class)).thenReturn("http://a/b.png");
        when(row.get("notify_inapp", Boolean.class)).thenReturn(true);
        when(row.get("notify_email", Boolean.class)).thenReturn(false);

        repository.findByUserId(USER_ID).subscribe();

        UserProfile mapped = captureBiFunction(profileRowsFetchSpec, UserProfile.class).apply(row, rowMetadata);

        assertThat(mapped.getUserId()).isEqualTo(userId);
        assertThat(mapped.getJobRole()).isEqualTo("Engineer");
        assertThat(mapped.getCurrentLevel()).isEqualTo("Middle");
        assertThat(mapped.getTechnologies()).containsExactly("Java", "Kotlin");
        assertThat(mapped.getLocation()).isEqualTo("Hanoi");
        assertThat(mapped.getBio()).isEqualTo("bio text");
        assertThat(mapped.getAvatarUrl()).isEqualTo("http://a/b.png");
        assertThat(mapped.getNotifyInapp()).isTrue();
        assertThat(mapped.getNotifyEmail()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByUserId_rowMapper_withNullTechnologies_mapsToEmptyList() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(profileRowsFetchSpec);
        when(profileRowsFetchSpec.one()).thenReturn(Mono.empty());
        when(row.get("technologies", String[].class)).thenReturn(null);

        repository.findByUserId(USER_ID).subscribe();

        UserProfile mapped = captureBiFunction(profileRowsFetchSpec, UserProfile.class).apply(row, rowMetadata);

        assertThat(mapped.getTechnologies()).isEmpty();
    }

    @Test
    void upsert_withAllFieldsPresent_bindsEveryColumn() {
        UserProfile profile = UserProfile.builder().userId(UUID.fromString(USER_ID))
                .jobRole("Engineer").currentLevel("Senior").technologies(List.of("Java", "Go")).location("Hanoi")
                .bio("bio text").avatarUrl("http://a/b.png").notifyInapp(false).notifyEmail(true).build();

        when(dbClient.sql(
                "INSERT INTO user_profile (user_id, job_role, current_level, technologies, location, bio, avatar_url, notify_inapp, notify_email, updated_at) " +
                        "VALUES (:user_id, :job_role, :current_level, :technologies, :location, :bio, :avatar_url, :notify_inapp, :notify_email, :updated_at) " +
                        "ON CONFLICT (user_id) DO UPDATE SET " +
                        "job_role = EXCLUDED.job_role, current_level = EXCLUDED.current_level, technologies = EXCLUDED.technologies, location = EXCLUDED.location, " +
                        "bio = EXCLUDED.bio, avatar_url = EXCLUDED.avatar_url, " +
                        "notify_inapp = EXCLUDED.notify_inapp, notify_email = EXCLUDED.notify_email, updated_at = EXCLUDED.updated_at"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.upsert(profile))
                .assertNext(saved -> assertThat(saved.getUserId()).isEqualTo(UUID.fromString(USER_ID)))
                .verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
        verify(executeSpec).bind("technologies", new String[] { "Java", "Go" });
        verify(executeSpec).bind("notify_inapp", false);
        verify(executeSpec).bind("notify_email", true);
        verify(executeSpec).bind("job_role", "Engineer");
        verify(executeSpec).bind("current_level", "Senior");
        verify(executeSpec).bind("location", "Hanoi");
        verify(executeSpec).bind("bio", "bio text");
        verify(executeSpec).bind("avatar_url", "http://a/b.png");
    }

    @Test
    void upsert_withNullTechnologies_bindsEmptyArray() {
        UserProfile profile = UserProfile.builder().userId(UUID.fromString(USER_ID)).technologies(null).build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.upsert(profile)).expectNextCount(1).verifyComplete();

        verify(executeSpec).bind("technologies", new String[0]);
    }

    @Test
    void upsert_withNullNotifyFlags_defaultsBothToTrue() {
        UserProfile profile = UserProfile.builder().userId(UUID.fromString(USER_ID))
                .notifyInapp(null).notifyEmail(null).build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.upsert(profile)).expectNextCount(1).verifyComplete();

        verify(executeSpec).bind("notify_inapp", Boolean.TRUE);
        verify(executeSpec).bind("notify_email", Boolean.TRUE);
    }

    @Test
    void upsert_withAllNullableStringFields_usesBindNull_notBind() {
        UserProfile profile = UserProfile.builder().userId(UUID.fromString(USER_ID))
                .jobRole(null).currentLevel(null).location(null).bio(null).avatarUrl(null).build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        // Real R2DBC throws on .bind(name, null) — this simulates that contract so a regression
        // to bind() instead of bindNull() for any of these nullable columns fails the test.
        lenient().when(executeSpec.bind(eq("job_role"), isNull())).thenThrow(new IllegalArgumentException("job_role"));
        lenient().when(executeSpec.bind(eq("current_level"), isNull())).thenThrow(new IllegalArgumentException("current_level"));
        lenient().when(executeSpec.bind(eq("location"), isNull())).thenThrow(new IllegalArgumentException("location"));
        lenient().when(executeSpec.bind(eq("bio"), isNull())).thenThrow(new IllegalArgumentException("bio"));
        lenient().when(executeSpec.bind(eq("avatar_url"), isNull())).thenThrow(new IllegalArgumentException("avatar_url"));
        when(executeSpec.bindNull(anyString(), eq(String.class))).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.upsert(profile)).expectNextCount(1).verifyComplete();

        verify(executeSpec).bindNull("job_role", String.class);
        verify(executeSpec).bindNull("current_level", String.class);
        verify(executeSpec).bindNull("location", String.class);
        verify(executeSpec).bindNull("bio", String.class);
        verify(executeSpec).bindNull("avatar_url", String.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findSubscribersByTechnology_bindsTechAsSingletonArray_andMapsRows() {
        when(dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email " +
                        "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                        "WHERE p.technologies @> :tech AND (p.notify_inapp = true OR p.notify_email = true)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("tech"), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(recipientRowsFetchSpec);
        UUID userId = UUID.fromString(USER_ID);
        when(recipientRowsFetchSpec.all()).thenReturn(Flux.just(new NotificationRecipient(userId, "a@b.com", true, false)));

        StepVerifier.create(repository.findSubscribersByTechnology("Java"))
                .expectNextMatches(r -> r.userId().equals(userId) && r.email().equals("a@b.com"))
                .verifyComplete();

        verify(executeSpec).bind("tech", new String[] { "Java" });
    }

    @Test
    @SuppressWarnings("unchecked")
    void findSubscribersByTechnology_rowMapper_mapsAllFourColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(recipientRowsFetchSpec);
        when(recipientRowsFetchSpec.all()).thenReturn(Flux.empty());
        UUID userId = UUID.fromString(USER_ID);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("email", String.class)).thenReturn("a@b.com");
        when(row.get("notify_inapp", Boolean.class)).thenReturn(true);
        when(row.get("notify_email", Boolean.class)).thenReturn(null);

        repository.findSubscribersByTechnology("Java").subscribe();

        NotificationRecipient mapped = captureBiFunction(recipientRowsFetchSpec, NotificationRecipient.class).apply(row, rowMetadata);

        assertThat(mapped.userId()).isEqualTo(userId);
        assertThat(mapped.email()).isEqualTo("a@b.com");
        assertThat(mapped.notifyInapp()).isTrue();
        assertThat(mapped.notifyEmail()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findJobMatchSubscribers_bindsTechsArray_andMapsMatchesCurrentSkillsColumn() {
        when(dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email, " +
                        "(p.technologies && :techs) AS matches_current " +
                        "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                        "WHERE (p.technologies && :techs OR p.target_skills && :techs) " +
                        "AND (p.notify_inapp = true OR p.notify_email = true)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("techs"), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(jobMatchRowsFetchSpec);
        UUID userId = UUID.fromString(USER_ID);
        when(jobMatchRowsFetchSpec.all()).thenReturn(Flux.just(new JobMatchSubscriber(userId, "a@b.com", true, true, true)));

        StepVerifier.create(repository.findJobMatchSubscribers(List.of("Java", "Go")))
                .expectNextMatches(m -> m.userId().equals(userId) && m.matchesCurrentSkills())
                .verifyComplete();

        verify(executeSpec).bind("techs", new String[] { "Java", "Go" });
    }

    @Test
    @SuppressWarnings("unchecked")
    void findJobMatchSubscribers_rowMapper_mapsAllFiveColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(jobMatchRowsFetchSpec);
        when(jobMatchRowsFetchSpec.all()).thenReturn(Flux.empty());
        UUID userId = UUID.fromString(USER_ID);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("email", String.class)).thenReturn("a@b.com");
        when(row.get("notify_inapp", Boolean.class)).thenReturn(false);
        when(row.get("notify_email", Boolean.class)).thenReturn(true);
        when(row.get("matches_current", Boolean.class)).thenReturn(false);

        repository.findJobMatchSubscribers(List.of("Java")).subscribe();

        JobMatchSubscriber mapped = captureBiFunction(jobMatchRowsFetchSpec, JobMatchSubscriber.class).apply(row, rowMetadata);

        assertThat(mapped.userId()).isEqualTo(userId);
        assertThat(mapped.email()).isEqualTo("a@b.com");
        assertThat(mapped.notifyInapp()).isFalse();
        assertThat(mapped.notifyEmail()).isTrue();
        assertThat(mapped.matchesCurrentSkills()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findSubscribersWithAnyTechnology_hasNoBindings_andMapsRows() {
        when(dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email " +
                        "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                        "WHERE p.technologies IS NOT NULL AND array_length(p.technologies, 1) > 0 " +
                        "AND (p.notify_inapp = true OR p.notify_email = true)"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(recipientRowsFetchSpec);
        UUID userId = UUID.fromString(USER_ID);
        when(recipientRowsFetchSpec.all()).thenReturn(Flux.just(new NotificationRecipient(userId, "a@b.com", true, true)));

        StepVerifier.create(repository.findSubscribersWithAnyTechnology())
                .expectNextMatches(r -> r.userId().equals(userId))
                .verifyComplete();

        verify(executeSpec, never()).bind(anyString(), any());
    }

    @Test
    void updateTargetSkills_bindsUserIdAndSkillsArray_returnsRowsUpdated() {
        when(dbClient.sql("UPDATE user_profile SET target_skills = :skills WHERE user_id = :user_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("user_id"), any())).thenReturn(executeSpec);
        when(executeSpec.bind(eq("skills"), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.updateTargetSkills(USER_ID, List.of("Java", "Rust")))
                .expectNext(1L)
                .verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
        verify(executeSpec).bind("skills", new String[] { "Java", "Rust" });
    }

    @SuppressWarnings("unchecked")
    private <T> BiFunction<Row, RowMetadata, T> captureBiFunction(RowsFetchSpec<T> ignoredForTypeInference, Class<T> ignoredType) {
        ArgumentCaptor<BiFunction<Row, RowMetadata, T>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }
}
