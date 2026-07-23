package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.features.auth.domain.User;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresUserRepository} — this
 * hand-written SQL/row-mapping code (including the {@code security_stamp} column added by the
 * RBAC work) silently breaks on a typo'd column name or wrong bind order without a test like
 * this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresUserRepositoryTest {

    private static final String SELECT_COLUMNS =
            "id, email, password_hash, full_name, role, status, subscription_tier, security_stamp, created_at, updated_at";

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<User> userRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Boolean> booleanRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresUserRepository repository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        repository = new PostgresUserRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private Function<Object, User> captureRowMapper() {
        ArgumentCaptor<Function<Object, User>> captor = ArgumentCaptor.forClass(Function.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    private void stubUserRow(UUID id, String email, String passwordHash, String fullName, String role,
                              String status, String tier, UUID securityStamp, LocalDateTime createdAt, LocalDateTime updatedAt) {
        when(row.get("id")).thenReturn(id);
        when(row.get("email", String.class)).thenReturn(email);
        when(row.get("password_hash", String.class)).thenReturn(passwordHash);
        when(row.get("full_name", String.class)).thenReturn(fullName);
        when(row.get("role", String.class)).thenReturn(role);
        when(row.get("status", String.class)).thenReturn(status);
        when(row.get("subscription_tier", String.class)).thenReturn(tier);
        when(row.get("security_stamp", UUID.class)).thenReturn(securityStamp);
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("updated_at", LocalDateTime.class)).thenReturn(updatedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByEmail_bindsEmailAndMapsRowToUser() {
        when(dbClient.sql("SELECT " + SELECT_COLUMNS + " FROM users WHERE email = :email"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("email", "a@b.com")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(userRowsFetchSpec);
        when(userRowsFetchSpec.one()).thenReturn(Mono.just(User.builder().email("a@b.com").build()));

        StepVerifier.create(repository.findByEmail("a@b.com"))
                .assertNext(u -> assertThat(u.getEmail()).isEqualTo("a@b.com"))
                .verifyComplete();

        verify(executeSpec).bind("email", "a@b.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsIdAndMapsRowToUser() {
        when(dbClient.sql("SELECT " + SELECT_COLUMNS + " FROM users WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", UUID.fromString(USER_ID))).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(userRowsFetchSpec);
        when(userRowsFetchSpec.one()).thenReturn(Mono.just(User.builder().id(UUID.fromString(USER_ID)).build()));

        StepVerifier.create(repository.findById(USER_ID))
                .assertNext(u -> assertThat(u.getId()).isEqualTo(UUID.fromString(USER_ID)))
                .verifyComplete();

        verify(executeSpec).bind("id", UUID.fromString(USER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_mapsAllColumns_includingSecurityStamp() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(userRowsFetchSpec);
        when(userRowsFetchSpec.one()).thenReturn(Mono.empty());
        UUID id = UUID.fromString(USER_ID);
        UUID stamp = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        stubUserRow(id, "a@b.com", "hash", "Alice", "admin", "active", "pro", stamp, now, now);

        repository.findById(USER_ID).subscribe();
        User mapped = captureRowMapper().apply(row);

        assertThat(mapped.getId()).isEqualTo(id);
        assertThat(mapped.getEmail()).isEqualTo("a@b.com");
        assertThat(mapped.getPasswordHash()).isEqualTo("hash");
        assertThat(mapped.getFullName()).isEqualTo("Alice");
        assertThat(mapped.getRole()).isEqualTo("admin");
        assertThat(mapped.getStatus()).isEqualTo("active");
        assertThat(mapped.getSubscriptionTier()).isEqualTo("pro");
        assertThat(mapped.getSecurityStamp()).isEqualTo(stamp);
        assertThat(mapped.getCreatedAt()).isEqualTo(now);
        assertThat(mapped.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void existsByEmail_returnsTrue_whenRowFound() {
        when(dbClient.sql("SELECT 1 FROM users WHERE email = :email")).thenReturn(executeSpec);
        when(executeSpec.bind("email", "a@b.com")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.just(true));

        StepVerifier.create(repository.existsByEmail("a@b.com"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void existsByEmail_returnsFalse_whenNoRowFound() {
        when(dbClient.sql("SELECT 1 FROM users WHERE email = :email")).thenReturn(executeSpec);
        when(executeSpec.bind("email", "missing@b.com")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.existsByEmail("missing@b.com"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_ordersByCreatedAtDesc_andMapsEveryRow() {
        when(dbClient.sql("SELECT " + SELECT_COLUMNS + " FROM users ORDER BY created_at DESC"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(userRowsFetchSpec);
        when(userRowsFetchSpec.all()).thenReturn(Flux.just(
                User.builder().email("a@b.com").build(), User.builder().email("c@d.com").build()));

        StepVerifier.create(repository.findAll())
                .expectNextMatches(u -> u.getEmail().equals("a@b.com"))
                .expectNextMatches(u -> u.getEmail().equals("c@d.com"))
                .verifyComplete();
    }

    @Test
    void deleteById_bindsIdAndReturnsRowsUpdated() {
        when(dbClient.sql("DELETE FROM users WHERE id = :id")).thenReturn(executeSpec);
        when(executeSpec.bind("id", UUID.fromString(USER_ID))).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteById(USER_ID))
                .expectNext(1L)
                .verifyComplete();

        verify(executeSpec).bind("id", UUID.fromString(USER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_selectsCountStar_andMapsToLong() {
        when(dbClient.sql("SELECT COUNT(*) AS c FROM users")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(42L));

        StepVerifier.create(repository.countAll())
                .expectNext(42L)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAdmins_bindsAdminRole_andMapsEveryRow() {
        when(dbClient.sql("SELECT " + SELECT_COLUMNS + " FROM users WHERE role = :role"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("role", "admin")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(userRowsFetchSpec);
        when(userRowsFetchSpec.all()).thenReturn(Flux.just(User.builder().role("admin").build()));

        StepVerifier.create(repository.findAdmins())
                .expectNextMatches(u -> u.getRole().equals("admin"))
                .verifyComplete();

        verify(executeSpec).bind("role", "admin");
    }

    @Test
    void save_withNoId_insertsAllColumns_includingGeneratedSecurityStamp() {
        User user = User.builder().email("new@b.com").passwordHash("hash").fullName("New User")
                .role("user").build();

        when(dbClient.sql(
                "INSERT INTO users (id, email, password_hash, full_name, role, status, subscription_tier, security_stamp, created_at, updated_at) " +
                        "VALUES (:id, :email, :password_hash, :full_name, :role, :status, :subscription_tier, :security_stamp, :created_at, :updated_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.save(user))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getSecurityStamp()).isNotNull();
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(executeSpec).bind("email", "new@b.com");
        verify(executeSpec).bind("password_hash", "hash");
        verify(executeSpec).bind("role", "user");
        verify(executeSpec).bind("status", "active");
        verify(executeSpec).bind("subscription_tier", "free");
        verify(executeSpec).bind("full_name", "New User");
    }

    @Test
    void save_withNoId_andNoFullName_bindsNullForFullName() {
        User user = User.builder().email("new@b.com").passwordHash("hash").role("user").build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        // Simulates the real R2DBC contract where .bind(name, null) throws — this test would
        // fail if the repository regressed to calling bind() instead of bindNull() for a null
        // full_name.
        lenient().when(executeSpec.bind(eq("full_name"), isNull()))
                .thenThrow(new IllegalArgumentException("bind value must not be null"));
        when(executeSpec.bindNull("full_name", String.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.save(user))
                .assertNext(saved -> assertThat(saved.getFullName()).isNull())
                .verifyComplete();

        verify(executeSpec).bindNull("full_name", String.class);
    }

    @Test
    void save_withId_updatesAllColumns_andDoesNotBindCreatedAt() {
        User user = User.builder().id(UUID.fromString(USER_ID)).email("existing@b.com").passwordHash("hash2")
                .fullName("Existing").role("admin").status("suspended").subscriptionTier("pro")
                .securityStamp(UUID.randomUUID()).build();

        when(dbClient.sql(
                "UPDATE users SET email = :email, password_hash = :password_hash, full_name = :full_name, role = :role, " +
                        "status = :status, subscription_tier = :subscription_tier, security_stamp = :security_stamp, updated_at = :updated_at " +
                        "WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.save(user))
                .assertNext(saved -> assertThat(saved.getUpdatedAt()).isNotNull())
                .verifyComplete();

        verify(executeSpec).bind("id", UUID.fromString(USER_ID));
        verify(executeSpec).bind("email", "existing@b.com");
        verify(executeSpec).bind("status", "suspended");
        verify(executeSpec).bind("subscription_tier", "pro");
        verify(executeSpec, never()).bind(eq("created_at"), any());
    }
}
