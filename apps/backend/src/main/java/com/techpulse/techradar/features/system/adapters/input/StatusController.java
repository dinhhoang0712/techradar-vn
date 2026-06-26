package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.application.AdminService;
import com.techpulse.techradar.features.system.domain.AppSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public system status / feature-flag endpoint polled by the web &amp; mobile clients.
 * Exposes the maintenance and feature toggles stored in the {@code settings} table as a flat object.
 */
@Tag(name = "Status", description = "Public system status and feature flags")
@RestController
@RequestMapping("/status")
@RequiredArgsConstructor
public class StatusController {

    /** Flags the clients expect, with safe defaults when a row is missing. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "maintenance_web", "false",
            "maintenance_mobile", "false",
            "feature_graph", "true",
            "feature_chat", "true",
            "feature_rag", "true"
    );

    private final AdminService adminService;

    @Operation(summary = "Get maintenance and feature-flag status")
    @GetMapping
    public Mono<ResponseEntity<Map<String, String>>> status() {
        // Returned BARE (no ApiResponse envelope): AppContext reads res.maintenance_web directly.
        return adminService.getAllSettings()
                .collectMap(AppSettings::getKey, AppSettings::getValue)
                .map(stored -> {
                    Map<String, String> flags = new LinkedHashMap<>(DEFAULTS);
                    stored.forEach((k, v) -> {
                        if (v != null) {
                            flags.put(k, v);
                        }
                    });
                    return ResponseEntity.ok(flags);
                })
                .onErrorReturn(ResponseEntity.ok(new LinkedHashMap<>(DEFAULTS)));
    }
}
