package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostImageRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresPostImageRepository}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresPostImageRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<PostImageRepository.ImageRow> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresPostImageRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresPostImageRepository(dbClient);
    }

    @Test
    void insert_bindsAllSixColumns() {
        UUID id = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        byte[] data = "fake-bytes".getBytes(StandardCharsets.UTF_8);
        LocalDateTime createdAt = LocalDateTime.now();

        when(dbClient.sql(
                "INSERT INTO post_image (id, post_id, ordinal, content_type, data, created_at) " +
                        "VALUES (:id, :post_id, :ordinal, :content_type, :data, :created_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(id, postId, 2, "image/png", data, createdAt)).verifyComplete();

        verify(executeSpec).bind("id", id);
        verify(executeSpec).bind("post_id", postId);
        verify(executeSpec).bind("ordinal", 2);
        verify(executeSpec).bind("content_type", "image/png");
        verify(executeSpec).bind("data", data);
        verify(executeSpec).bind("created_at", createdAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsImageId_andMapsRow() {
        UUID imageId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        byte[] data = "png-bytes".getBytes(StandardCharsets.UTF_8);

        when(dbClient.sql("SELECT post_id, content_type, data FROM post_image WHERE id = :id")).thenReturn(executeSpec);
        when(executeSpec.bind("id", imageId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(new PostImageRepository.ImageRow(postId, "image/jpeg", data)));

        StepVerifier.create(repository.findById(imageId))
                .assertNext(image -> {
                    assertThat(image.postId()).isEqualTo(postId);
                    assertThat(image.contentType()).isEqualTo("image/jpeg");
                    assertThat(image.data()).isEqualTo(data);
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_rowMapper_mapsAllThreeColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());

        UUID postId = UUID.randomUUID();
        byte[] data = "bytes".getBytes(StandardCharsets.UTF_8);
        when(row.get("post_id", UUID.class)).thenReturn(postId);
        when(row.get("content_type", String.class)).thenReturn("image/gif");
        when(row.get("data", byte[].class)).thenReturn(data);

        repository.findById(UUID.randomUUID()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, PostImageRepository.ImageRow>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        PostImageRepository.ImageRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.postId()).isEqualTo(postId);
        assertThat(mapped.contentType()).isEqualTo("image/gif");
        assertThat(mapped.data()).isEqualTo(data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_returnsEmptyWhenNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete();
    }
}
