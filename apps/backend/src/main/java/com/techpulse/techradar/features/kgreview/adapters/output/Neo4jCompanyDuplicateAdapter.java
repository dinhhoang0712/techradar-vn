package com.techpulse.techradar.features.kgreview.adapters.output;

import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup;
import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup.Candidate;
import com.techpulse.techradar.features.kgreview.ports.CompanyDuplicatePort;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Java port of {@code data-platform/gold/kg_health_audit.py}'s
 * {@code _check_company_near_duplicates} — same boilerplate-stripping + word-boundary heuristic,
 * kept in sync deliberately (see that file's comment for why substring-without-word-boundary
 * matching was rejected: single-syllable Vietnamese words like "học"/"đại"/"tại" collide across
 * dozens of unrelated companies).
 * <p>
 * Unlike the Python version this visits every unordered pair by INDEX rather than skipping when
 * {@code nameA >= nameB} lexicographically — that string-comparison shortcut has a gap where two
 * Company nodes sharing the exact same display name are never paired (equal strings satisfy
 * {@code >=}), which is an even more obvious duplicate signal than a legal-entity variant.
 */
@Component
public class Neo4jCompanyDuplicateAdapter implements CompanyDuplicatePort {

    private static final List<String> BOILERPLATE_PHRASES = List.of(
            "công\\s*ty\\s*trách\\s*nhiệm\\s*hữu\\s*hạn",
            "trách\\s*nhiệm\\s*hữu\\s*hạn",
            "công\\s*ty\\s*cổ\\s*phần",
            "công\\s*ty\\s*tnhh",
            "tổng\\s*công\\s*ty",
            "một\\s*thành\\s*viên",
            "\\bmtv\\b",
            "\\btnhh\\b",
            "\\bjsc\\b",
            "chi\\s*nhánh",
            "văn\\s*phòng\\s*đại\\s*diện",
            "tập\\s*đoàn",
            "\\bcông\\s*ty\\b",
            "\\bcổ\\s*phần\\b",
            "\\bco\\.?,?\\s*ltd\\.?\\b",
            "\\bltd\\.?\\b",
            "\\bcorporation\\b",
            "\\bcorp\\.?\\b",
            "\\bpro\\s*company\\b"
    );
    private static final Pattern BOILERPLATE_PATTERN = Pattern.compile(
            String.join("|", BOILERPLATE_PHRASES),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern NON_WORD_PATTERN =
            Pattern.compile("[^\\w\\s]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final int CORE_MIN_LEN = 6;
    private static final int NAME_MAX_LEN = 200;

    private final Driver driver;

    public Neo4jCompanyDuplicateAdapter(Driver driver) {
        this.driver = driver;
    }

    @Override
    public Flux<CompanyDuplicateGroup> detectNearDuplicates() {
        return Flux.defer(() -> Flux.fromIterable(detect())).subscribeOn(Schedulers.boundedElastic());
    }

    private List<CompanyDuplicateGroup> detect() {
        List<Candidate> allCompanies = fetchCompanies();
        List<CoredCandidate> cored = new ArrayList<>();
        for (Candidate c : allCompanies) {
            if (c.name() == null || c.name().isBlank() || c.name().length() > NAME_MAX_LEN) {
                continue;
            }
            String core = companyCore(c.name());
            if (core.length() >= CORE_MIN_LEN) {
                cored.add(new CoredCandidate(c, core));
            }
        }

        // shorter-core -> (companyId -> Candidate), preserves insertion order for stable output.
        Map<String, Map<String, Candidate>> groups = new LinkedHashMap<>();
        for (int i = 0; i < cored.size(); i++) {
            for (int j = i + 1; j < cored.size(); j++) {
                CoredCandidate a = cored.get(i);
                CoredCandidate b = cored.get(j);
                String shorter = a.core.length() <= b.core.length() ? a.core : b.core;
                String longer = a.core.length() <= b.core.length() ? b.core : a.core;
                if (a.core.equals(b.core) || wordBoundaryContains(shorter, longer)) {
                    Map<String, Candidate> group = groups.computeIfAbsent(shorter, k -> new LinkedHashMap<>());
                    group.put(a.candidate.id(), a.candidate);
                    group.put(b.candidate.id(), b.candidate);
                }
            }
        }

        return groups.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .map(e -> new CompanyDuplicateGroup(
                        e.getKey(),
                        e.getValue().values().stream()
                                .sorted((c1, c2) -> c1.name().compareTo(c2.name()))
                                .toList()
                ))
                .toList();
    }

    private List<Candidate> fetchCompanies() {
        try (Session session = driver.session()) {
            List<Candidate> result = new ArrayList<>();
            for (Record record : session.run("MATCH (c:Company) RETURN c.id AS id, c.name AS name").list()) {
                String id = record.get("id").isNull() ? null : record.get("id").asString();
                String name = record.get("name").isNull() ? null : record.get("name").asString();
                if (id != null) {
                    result.add(new Candidate(id, name));
                }
            }
            return result;
        }
    }

    private String companyCore(String name) {
        String n = BOILERPLATE_PATTERN.matcher(name).replaceAll(" ");
        n = NON_WORD_PATTERN.matcher(n).replaceAll(" ");
        n = WHITESPACE_PATTERN.matcher(n).replaceAll(" ").strip().toLowerCase();
        return n;
    }

    private boolean wordBoundaryContains(String shorter, String longer) {
        return (" " + longer + " ").contains(" " + shorter + " ");
    }

    private record CoredCandidate(Candidate candidate, String core) {
    }
}
