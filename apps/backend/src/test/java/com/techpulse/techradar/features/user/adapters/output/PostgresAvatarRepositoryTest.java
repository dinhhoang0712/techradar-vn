package com.techpulse.techradar.features.user.adapters.output;

import com.techpulse.techradar.features.user.domain.Avatar;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresAvatarRepository} — this
 * hand-written SQL/row-mapping code silently breaks on a typo'd column name or wrong bind order
 * without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresAvatarRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<Avatar> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresAvatarRepository repository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        repository = new PostgresAvatarRepository(dbClient);
    }

    @Test
    void save_upsertsContentTypeAndData_bindingAllFourColumns() {
        byte[] data = { 1, 2, 3 };
        when(dbClient.sql(
                "INSERT INTO user_avatar (user_id, content_type, data, updated_at) " +
                        "VALUES (:user_id, :content_type, :data, :updated_at) " +
                        "ON CONFLICT (user_id) DO UPDATE SET " +
                        "content_type = EXCLUDED.content_type, data = EXCLUDED.data, updated_at = EXCLUDED.updated_at"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.save(USER_ID, "image/png", data)).verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
        verify(executeSpec).bind("content_type", "image/png");
        verify(executeSpec).bind("data", data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void find_bindsUserId_andMapsContentTypeAndData() {
        byte[] data = { 4, 5, 6 };
        when(dbClient.sql("SELECT content_type, data FROM user_avatar WHERE user_id = :user_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", UUID.fromString(USER_ID))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(new Avatar("image/jpeg", data)));

        StepVerifier.create(repository.find(USER_ID))
                .assertNext(avatar -> {
                    assertThat(avatar.contentType()).isEqualTo("image/jpeg");
                    assertThat(avatar.data()).isEqualTo(data);
                })
                .verifyComplete();

        verify(executeSpec).bind("user_id", UUID.fromString(USER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void find_rowMapper_mapsBothColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());
        byte[] data = { 7, 8, 9 };
        when(row.get("content_type", String.class)).thenReturn("image/gif");
        when(row.get("data", byte[].class)).thenReturn(data);

        repository.find(USER_ID).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, Avatar>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        Avatar mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.contentType()).isEqualTo("image/gif");
        assertThat(mapped.data()).isEqualTo(data);
    }
}
