package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.CmsContent;
import com.techpulse.techradar.features.system.ports.CmsRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsServiceTest {

    @Mock
    private CmsRepository cmsRepository;

    private CmsService service;

    @BeforeEach
    void setUp() {
        service = new CmsService(cmsRepository);
    }

    @Test
    void list_returnsAllContentFromRepository() {
        when(cmsRepository.findAll()).thenReturn(Flux.just(
                CmsContent.builder().id(UUID.randomUUID()).title("A").build(),
                CmsContent.builder().id(UUID.randomUUID()).title("B").build()));

        StepVerifier.create(service.list()).expectNextCount(2).verifyComplete();
    }

    @Test
    void create_defaultsStatusToPending_whenStatusBlank() {
        when(cmsRepository.insert(any())).thenAnswer(inv -> {
            CmsContent c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return Mono.just(c);
        });

        StepVerifier.create(service.create("Báo cáo Q3", "Report", LocalDate.of(2026, 7, 1), "  "))
                .assertNext(created -> {
                    assertThat(created.getTitle()).isEqualTo("Báo cáo Q3");
                    assertThat(created.getType()).isEqualTo("Report");
                    assertThat(created.getStatus()).isEqualTo("Pending");
                })
                .verifyComplete();
    }

    @Test
    void create_keepsProvidedStatus_whenPresent() {
        when(cmsRepository.insert(any())).thenAnswer(inv -> {
            CmsContent c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return Mono.just(c);
        });

        StepVerifier.create(service.create("Job listing", "Job", LocalDate.now(), "Published"))
                .assertNext(created -> assertThat(created.getStatus()).isEqualTo("Published"))
                .verifyComplete();
    }

    @Test
    void update_fails_whenContentNotFound() {
        when(cmsRepository.findById("missing-id")).thenReturn(Mono.empty());

        StepVerifier.create(service.update("missing-id", "New title", null, null, null))
                .expectError(NotFoundException.class)
                .verify();

        verify(cmsRepository, never()).update(any());
    }

    @Test
    void update_appliesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        CmsContent existing = CmsContent.builder()
                .id(id).title("Old title").type("Report").contentDate(LocalDate.of(2026, 1, 1)).status("Pending")
                .build();
        when(cmsRepository.findById(id.toString())).thenReturn(Mono.just(existing));
        when(cmsRepository.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.update(id.toString(), "New title", null, null, "Published"))
                .assertNext(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("New title");
                    assertThat(updated.getType()).isEqualTo("Report");
                    assertThat(updated.getContentDate()).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(updated.getStatus()).isEqualTo("Published");
                })
                .verifyComplete();

        ArgumentCaptor<CmsContent> captor = ArgumentCaptor.forClass(CmsContent.class);
        verify(cmsRepository).update(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New title");
    }

    @Test
    void delete_fails_whenNoRowsDeleted() {
        when(cmsRepository.deleteById("missing-id")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.delete("missing-id"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void delete_completes_whenRowDeleted() {
        when(cmsRepository.deleteById("id-1")).thenReturn(Mono.just(1L));

        StepVerifier.create(service.delete("id-1")).verifyComplete();
    }
}
