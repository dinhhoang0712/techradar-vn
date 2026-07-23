package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import com.techpulse.techradar.features.user.application.AdminUserService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.paging.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read-only view over the audit trail recorded by {@link AuditLogService}.
 */
@Tag(name = "Admin", description = "Audit trail of admin mutations")
@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
public class AuditLogAdminController {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private final AuditLogService auditLogService;
    private final AdminUserService userService;

    @Operation(summary = "List audit log entries, newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public Mono<ResponseEntity<ApiResponse<List<AuditLogView>>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);

        // Small admin user base — one full listUsers() per request is cheap and avoids an N+1
        // lookup per audit row just to show a human-readable actor instead of a raw UUID.
        Mono<Map<String, String>> actorEmailsById = userService.listUsers()
                .collectMap(u -> u.getId().toString(), u -> u.getEmail());

        return actorEmailsById.flatMap(actorEmails ->
                auditLogService.list(pageRequest.size(), pageRequest.offset())
                        .map(e -> AuditLogView.from(e, actorEmails.get(e.getActorId().toString())))
                        .collectList()
                        .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Audit log retrieved"))));
    }

    @Value
    @Builder
    public static class AuditLogView {
        String id;
        String actorId;
        String actorEmail;
        String action;
        String targetType;
        String targetId;
        String details;
        LocalDateTime createdAt;

        static AuditLogView from(AuditLogEntry e, String actorEmail) {
            return AuditLogView.builder()
                    .id(e.getId().toString())
                    .actorId(e.getActorId().toString())
                    .actorEmail(actorEmail)
                    .action(e.getAction())
                    .targetType(e.getTargetType())
                    .targetId(e.getTargetId())
                    .details(e.getDetails())
                    .createdAt(e.getCreatedAt())
                    .build();
        }
    }
}
