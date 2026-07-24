package com.techpulse.techradar.features.kafka.domain;

import com.techpulse.techradar.features.kafka.adapters.output.TechAliasCache;
import com.techpulse.techradar.features.kafka.event.Entities;
import com.techpulse.techradar.features.kafka.ports.CompanyNameProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the fix where "Golang"/"ML" were extracted as their own Technology
 * name instead of being resolved to "Go"/"Machine Learning" via
 * dp_tech_alias_map (TechAliasCache) — see EntityExtractionService.extractTech
 * and .extractEntities (raw job skill tags). Also covers extractOrg/extractLoc
 * — added after discovering ORG/LOC always came back empty (no NER, and no
 * dictionary source for them existed before CompanyNameProvider/LOCATION_KEYWORDS).
 */
@ExtendWith(MockitoExtension.class)
class EntityExtractionServiceTest {

    @Mock
    private TechAliasCache techAliasCache;

    @Mock
    private CompanyNameProvider companyNameProvider;

    private EntityExtractionService service;

    @BeforeEach
    void setUp() {
        // Mặc định: không có alias nào khớp — resolve() trả nguyên tên đã strip,
        // giống hành vi cache rỗng lúc mới khởi động (trước khi refresh() lần đầu). lenient()
        // vì các test chỉ kiểm tra extractOrg/extractLoc (text không chứa từ khoá tech nào) sẽ
        // không bao giờ gọi tới resolve() — Mockito strict stubbing coi đó là "unnecessary" nếu
        // không đánh dấu lenient.
        lenient().when(techAliasCache.resolve(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).strip());
        lenient().when(companyNameProvider.knownCompanyNames()).thenReturn(List.of());
        service = new EntityExtractionService(techAliasCache, companyNameProvider);
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

    @Test
    void extractEntities_detectsKnownCompanyMentionedInText() {
        when(companyNameProvider.knownCompanyNames()).thenReturn(List.of("FPT Software", "Tiki"));

        Entities entities = service.extractEntities("FPT Software vừa công bố sản phẩm AI mới.", List.of());

        assertThat(entities.getOrg()).containsExactly("FPT Software");
    }

    @Test
    void extractEntities_doesNotDetectCompanyNotInKnownList() {
        // Regression guard: giới hạn có chủ đích của dictionary-based approach — company chưa
        // từng biết qua Job posting sẽ không được nhận diện, dù được nhắc rõ ràng trong text.
        when(companyNameProvider.knownCompanyNames()).thenReturn(List.of("Tiki"));

        Entities entities = service.extractEntities("Shopee vừa mở rộng thị trường.", List.of());

        assertThat(entities.getOrg()).isEmpty();
    }

    @Test
    void extractEntities_companyMatchIsWordBoundaryAware() {
        // Regression guard cho phát hiện thật khi xây Company near-duplicate audit: substring
        // thô bắt nhầm "FPT" nằm lọt trong 1 chuỗi không liên quan (vd "sFPTs") — chỉ khớp khi
        // company nằm trọn ở ranh giới từ.
        when(companyNameProvider.knownCompanyNames()).thenReturn(List.of("FPT"));

        Entities entities = service.extractEntities("Từ khoá lạ: sFPTs không phải công ty.", List.of());

        assertThat(entities.getOrg()).isEmpty();
    }

    @Test
    void extractEntities_detectsKnownLocationAndAlias() {
        Entities entities = service.extractEntities("Tuyển dụng tại TP.HCM và Đà Nẵng.", List.of());

        assertThat(entities.getLoc()).containsExactlyInAnyOrder("Hồ Chí Minh", "Đà Nẵng");
    }

    @Test
    void extractEntities_returnsEmptyLocWhenNoProvinceMentioned() {
        Entities entities = service.extractEntities("Làm việc từ xa, không yêu cầu địa điểm.", List.of());

        assertThat(entities.getLoc()).isEmpty();
    }
}
