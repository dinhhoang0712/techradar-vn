package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

/** Company profile: industry/size fields and Article-[:MENTIONS]->Company news feed. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")
class CompanyIntegrationTest extends IntegrationTestSupport {

    @Test
    void list_includesIndustryAndSizeWhenPresent() {
        String companyName = "Acme Industry Corp " + UUID.randomUUID();
        String companyId = "test-company-" + UUID.randomUUID();
        seedCompany(companyId, companyName, "Hà Nội", "Fintech", "100-500 nhân sự");

        web.get().uri("/api/v1/companies?q=" + companyName).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].industry").isEqualTo("Fintech")
                .jsonPath("$.data[0].size").isEqualTo("100-500 nhân sự");
    }

    @Test
    void list_omitsIndustryAndSizeWhenAbsent() {
        String companyName = "Acme No Meta Corp " + UUID.randomUUID();
        String companyId = "test-company-" + UUID.randomUUID();
        seedCompany(companyId, companyName, "Hà Nội");

        web.get().uri("/api/v1/companies?q=" + companyName).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].industry").doesNotExist()
                .jsonPath("$.data[0].size").doesNotExist();
    }

    @Test
    void mentions_returnsArticlesMentioningCompanyMostRecentFirst() {
        String companyId = "test-company-" + UUID.randomUUID();
        seedCompany(companyId, "Acme Mentioned Corp " + UUID.randomUUID(), "Đà Nẵng");
        seedCompanyMention(companyId, "Older news", "https://example.com/older", "2026-01-01", "VNExpress");
        seedCompanyMention(companyId, "Newer news", "https://example.com/newer", "2026-06-01", "GenK");

        web.get().uri("/api/v1/companies/" + companyId + "/mentions").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].title").isEqualTo("Newer news")
                .jsonPath("$.data[0].sourcePlatform").isEqualTo("GenK")
                .jsonPath("$.data[1].title").isEqualTo("Older news");
    }

    @Test
    void mentions_returnsEmptyListWhenCompanyHasNoMentions() {
        String companyId = "test-company-" + UUID.randomUUID();
        seedCompany(companyId, "Acme Unmentioned Corp " + UUID.randomUUID(), "Hồ Chí Minh");

        web.get().uri("/api/v1/companies/" + companyId + "/mentions").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0]").doesNotExist();
    }
}
