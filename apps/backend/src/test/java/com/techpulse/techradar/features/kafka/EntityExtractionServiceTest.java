package com.techpulse.techradar.features.kafka;

import com.techpulse.techradar.features.kafka.model.Entities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the fix where "Golang"/"ML" were extracted as their own Technology
 * name instead of being resolved to "Go"/"Machine Learning" via
 * dp_tech_alias_map (TechAliasCache) — see EntityExtractionService.extractTech
 * and .extractEntities (raw job skill tags).
 */
@ExtendWith(MockitoExtension.class)
class EntityExtractionServiceTest {

    @Mock
    private TechAliasCache techAliasCache;

    private EntityExtractionService service;

    @BeforeEach
    void setUp() {
        // Mặc định: không có alias nào khớp — resolve() trả nguyên tên đã strip,
        // giống hành vi cache rỗng lúc mới khởi động (trước khi refresh() lần đầu).
        when(techAliasCache.resolve(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).strip());
        service = new EntityExtractionService(techAliasCache);
    }

    @Test
    void extractEntities_resolvesKeywordMatchedTechThroughAliasCache() {
        when(techAliasCache.resolve("Golang")).thenReturn("Go");

        Entities entities = service.extractEntities("Dự án dùng Golang cho backend.", List.of());

        assertThat(entities.getTech()).containsExactly("Go");
    }

    @Test
    void extractEntities_resolvesRawJobSkillTagThroughAliasCache() {
        when(techAliasCache.resolve("Golang")).thenReturn("Go");

        // Skill tag thô từ crawler — KHÔNG qua regex TECH_KEYWORDS, đi thẳng qua
        // nhánh jobSkills của extractEntities().
        Entities entities = service.extractEntities("", List.of("Golang"));

        assertThat(entities.getTech()).containsExactly("Go");
    }

    @Test
    void extractEntities_keepsOriginalNameWhenNoAliasMatches() {
        Entities entities = service.extractEntities("Viết bằng Rust.", List.of());

        assertThat(entities.getTech()).containsExactly("Rust");
    }

    @Test
    void extractEntities_dedupsWhenKeywordAndSkillTagResolveToSameCanonical() {
        when(techAliasCache.resolve("Golang")).thenReturn("Go");

        Entities entities = service.extractEntities("Dự án dùng Golang.", List.of("Golang"));

        assertThat(entities.getTech()).containsExactly("Go");
    }
}
