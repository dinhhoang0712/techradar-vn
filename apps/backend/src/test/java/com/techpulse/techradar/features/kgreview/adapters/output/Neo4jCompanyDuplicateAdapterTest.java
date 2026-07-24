package com.techpulse.techradar.features.kgreview.adapters.output;

import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Java port of {@code data-platform/gold/kg_health_audit.py}'s
 * {@code _check_company_near_duplicates} — pins the same boilerplate-stripping + word-boundary
 * behavior (see that file's comment for why plain substring matching was rejected).
 */
@ExtendWith(MockitoExtension.class)
class Neo4jCompanyDuplicateAdapterTest {

    @Mock
    private Driver driver;
    @Mock
    private Session session;
    @Mock
    private Result result;

    private Neo4jCompanyDuplicateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Neo4jCompanyDuplicateAdapter(driver);
        when(driver.session()).thenReturn(session);
        when(session.run("MATCH (c:Company) RETURN c.id AS id, c.name AS name")).thenReturn(result);
    }

    private static Record companyRecord(String id, String name) {
        Record record = mock(Record.class);
        Value idValue = mock(Value.class);
        when(idValue.isNull()).thenReturn(false);
        when(idValue.asString()).thenReturn(id);
        Value nameValue = mock(Value.class);
        when(nameValue.isNull()).thenReturn(name == null);
        if (name != null) {
            when(nameValue.asString()).thenReturn(name);
        }
        when(record.get("id")).thenReturn(idValue);
        when(record.get("name")).thenReturn(nameValue);
        return record;
    }

    private List<CompanyDuplicateGroup> detect() {
        return adapter.detectNearDuplicates().collectList().block();
    }

    @Test
    void detect_legalEntityVariants_groupTogether() {
        when(result.list()).thenReturn(List.of(
                companyRecord("fpt-software", "FPT Software"),
                companyRecord("fpt-corp", "Công Ty Cổ Phần Viễn Thông FPT Software")
        ));

        List<CompanyDuplicateGroup> groups = detect();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).companies()).extracting(CompanyDuplicateGroup.Candidate::id)
                .containsExactlyInAnyOrder("fpt-software", "fpt-corp");
    }

    @Test
    void detect_unrelatedCompaniesWithoutWordBoundaryMatch_notGrouped() {
        when(result.list()).thenReturn(List.of(
                companyRecord("vinsmart", "VinSmart"),
                companyRecord("insmart", "Insmart")
        ));

        List<CompanyDuplicateGroup> groups = detect();

        // "insmart" sits inside "vinsmart" as a raw substring ("vinsmart"[1..] == "insmart")
        // but NOT on a word boundary (" insmart " is not a substring of " vinsmart ") — must
        // not be grouped, even though these are 2 completely unrelated companies.
        assertThat(groups).isEmpty();
    }

    @Test
    void detect_identicalDisplayNames_areGrouped() {
        // Two different physical Company nodes sharing the exact same name — an even more
        // obvious duplicate signal than a legal-entity variant, and one the Python version's
        // lexicographic name_a >= name_b skip would miss entirely (see class javadoc).
        when(result.list()).thenReturn(List.of(
                companyRecord("id-1", "Vietnam Solutions"),
                companyRecord("id-2", "Vietnam Solutions")
        ));

        List<CompanyDuplicateGroup> groups = detect();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).companies()).hasSize(2);
    }

    @Test
    void detect_noCompanies_returnsEmpty() {
        when(result.list()).thenReturn(List.of());

        assertThat(detect()).isEmpty();
    }

    @Test
    void detect_shortCoreBelowMinLength_notGrouped() {
        // Boilerplate-only names strip down to an empty/very short core (< CORE_MIN_LEN) and
        // must be excluded even if their raw names happen to be identical after stripping.
        when(result.list()).thenReturn(List.of(
                companyRecord("c1", "Công Ty TNHH ABC"),
                companyRecord("c2", "Công Ty Cổ Phần ABC")
        ));

        // Core after stripping boilerplate is just "abc" (3 chars) — below CORE_MIN_LEN (6).
        assertThat(detect()).isEmpty();
    }

    @Test
    void detect_groupsSortedByDescendingSize() {
        when(result.list()).thenReturn(List.of(
                companyRecord("a1", "Alpha Solutions JSC"),
                companyRecord("a2", "Công Ty Cổ Phần Alpha Solutions"),
                companyRecord("a3", "Alpha Solutions Corp"),
                companyRecord("b1", "Beta Technologies Ltd"),
                companyRecord("b2", "Công Ty TNHH Beta Technologies")
        ));

        List<CompanyDuplicateGroup> groups = detect();

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).companies().size()).isGreaterThanOrEqualTo(groups.get(1).companies().size());
        assertThat(groups.get(0).companies()).hasSize(3); // the "alpha solutions" group
    }
}
