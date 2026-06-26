package com.techpulse.techradar.features.system.adapters.input;

import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.features.system.domain.CmsContent;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin CMS content CRUD (manage crawled reports / jobs / keywords).
 */
@Tag(name = "Admin", description = "Admin CMS content management")
@RestController
@RequestMapping("/admin/cms")
@RequiredArgsConstructor
public class AdminCmsController {

    private final CmsService cmsService;

    @Operation(summary = "List CMS content")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<CmsContent>>>> list() {
        return cmsService.list().collectList()
                .map(items -> ResponseEntity.ok(ApiResponse.success(items, "CMS content")));
    }

    @Operation(summary = "Create CMS content")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CmsContent>>> create(@RequestBody CmsContentRequest req) {
        return cmsService.create(req.getTitle(), req.getType(), req.getContentDate(), req.getStatus())
                .map(c -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(c, "CMS content created")));
    }

    @Operation(summary = "Update CMS content")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CmsContent>>> update(
            @PathVariable String id, @RequestBody CmsContentRequest req) {
        return cmsService.update(id, req.getTitle(), req.getType(), req.getContentDate(), req.getStatus())
                .map(c -> ResponseEntity.ok(ApiResponse.success(c, "CMS content updated")));
    }

    @Operation(summary = "Delete CMS content")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> delete(@PathVariable String id) {
        return cmsService.delete(id)
                .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body(ApiResponse.<Void>success(null, "CMS content deleted")));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CmsContentRequest {
        @NotBlank(message = "title is required")
        private String title;
        private String type;
        private LocalDate contentDate;
        private String status;
    }
}
