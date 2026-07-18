package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import com.techpulse.techradar.features.system.ports.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository);
    }

    private Context withActor(UUID actorId) {
        var auth = new UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of());
        SecurityContext securityContext = new SecurityContextImpl(auth);
        return ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));
    }

    @Test
    void record_insertsEntryWithResolvedActor() {
        UUID actorId = UUID.randomUUID();
        when(repository.insert(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.record("DELETE_USER", "USER", "u-123", "email=x@y.com")
                        .contextWrite(withActor(actorId)))
                .verifyComplete();

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).insert(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getActorId()).isEqualTo(actorId);
        assertThat(entry.getAction()).isEqualTo("DELETE_USER");
        assertThat(entry.getTargetType()).isEqualTo("USER");
        assertThat(entry.getTargetId()).isEqualTo("u-123");
        assertThat(entry.getDetails()).isEqualTo("email=x@y.com");
    }

    @Test
    void record_swallowsRepositoryFailure_insteadOfPropagating() {
        UUID actorId = UUID.randomUUID();
        when(repository.insert(any())).thenReturn(Mono.error(new RuntimeException("db down")));

        StepVerifier.create(service.record("DELETE_USER", "USER", "u-123", null)
                        .contextWrite(withActor(actorId)))
                .verifyComplete();
    }
}
