package com.techpulse.techradar.features.system.adapters.input;


import com.techpulse.techradar.features.system.application.AdminService;
import com.techpulse.techradar.features.system.domain.AppSettings;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin API controller.
 */
@Tag(name = "Admin", description = "Administration endpoints (admin only)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;


    @Operation(summary = "Get application settings")
    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<AppSettings>>>> getSettings() {
        return adminService.getAllSettings()
                .collectList()
                .map(settings -> ResponseEntity.ok(
                        ApiResponse.success(settings, "Settings retrieved")
                ));
    }

    @Operation(summary = "Get specific setting")
    @GetMapping("/settings/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppSettings>>> getSetting(
            @PathVariable String key
    ) {
        return adminService.getSetting(key)
                .map(setting -> ResponseEntity.ok(
                        ApiResponse.success(setting, "Setting retrieved")
                ))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update setting")
    @PutMapping("/settings/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppSettings>>> updateSetting(
            @PathVariable String key,
            @RequestBody UpdateSettingRequest request
    ) {
        return adminService.updateSetting(key, request.value(), request.description())
                .map(setting -> ResponseEntity.ok(
                        ApiResponse.success(setting, "Setting updated")
                ));
    }

    @Operation(summary = "Delete setting")
    @DeleteMapping("/settings/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteSetting(
            @PathVariable String key
    ) {
        return adminService.deleteSetting(key)
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).body(
                        ApiResponse.success(null, "Setting deleted")
                )));
    }

}
