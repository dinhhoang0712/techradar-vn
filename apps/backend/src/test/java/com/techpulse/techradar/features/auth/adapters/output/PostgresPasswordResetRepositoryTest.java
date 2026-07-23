package com.techpulse.techradar.features.auth.adapters.output;

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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresPasswordResetRepository} —
 * this hand-written SQL/row-mapping code silently breaks on a typo'd column name or wrong bind
 * order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresPasswordResetRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<String> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresPasswordResetRepository repository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        repository = new PostgresPasswordResetRepository(dbClient);
    }

    @Test
    void createToken_insertsTokenUserIdAndExpiry_thenReturnsTheGeneratedToken() {
        when(dbClient.sql("INSERT INTO password_reset (token, user_id, expires_at) VALUES (:token, :user_id, :expires_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("token"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", UUID.fromString(USER_ID))).thenReturn(executeSpec);
        when(executeSpec.bind(eq("expires_at"), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.createToken(USER_ID))
                .assertNext(token -> assertThat(token).isNotNull())
                .verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findValidUserId_selectsWithUsedFalseAndExpiryCheck_bindsTokenAndMapsUserId() {
        String token = "22222222-2222-2222-2222-222222222222";
        when(dbClient.sql(
                "SELECT user_id FROM password_reset " +
                        "WHERE token = :token AND used = false AND expires_at > now()"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("token", UUID.fromString(token))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(USER_ID));

        StepVerifier.create(repository.findValidUserId(token))
                .assertNext(userId -> assertThat(userId).isEqualTo(USER_ID))
                .verifyComplete();

        verify(executeSpec).bind("token", UUID.fromString(token));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findValidUserId_rowMapper_convertsUserIdColumnToString() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());
        UUID userIdColumn = UUID.fromString(USER_ID);
        when(row.get("user_id", UUID.class)).thenReturn(userIdColumn);

        repository.findValidUserId("22222222-2222-2222-2222-222222222222").subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, String>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        String mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped).isEqualTo(USER_ID);
    }

    @Test
    void markUsed_updatesUsedFlag_bindsTokenAndCompletes() {
        String token = "33333333-3333-3333-3333-333333333333";
        when(dbClient.sql("UPDATE password_reset SET used = true WHERE token = :token"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("token", UUID.fromString(token))).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.markUsed(token)).verifyComplete();

        verify(executeSpec).bind("token", UUID.fromString(token));
    }
}
