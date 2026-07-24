from __future__ import annotations

import gold.tech_dedup as tech_dedup

# ---------------------------------------------------------------------------
# Fakes — Postgres (giống pattern FakeConn/FakeCursor của test_silver_processor.py)
# ---------------------------------------------------------------------------


class FakeCursor:
    def __init__(self, fetchall_result=None):
        self.executed = []
        self._fetchall_result = fetchall_result or []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, params=None):
        self.executed.append((query, params))

    def fetchall(self):
        return self._fetchall_result


class FakeConn:
    def __init__(self, fetchall_result=None):
        self._fetchall_result = fetchall_result
        self.cursors = []
        self.commit_count = 0

    def cursor(self):
        cur = FakeCursor(self._fetchall_result)
        self.cursors.append(cur)
        return cur

    def commit(self):
        self.commit_count += 1


# ---------------------------------------------------------------------------
# Fakes — Neo4j driver/session (chỉ interface tối thiểu tech_dedup.py dùng)
# ---------------------------------------------------------------------------


class FakeNeo4jSession:
    def __init__(self, driver):
        self._driver = driver

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, params=None):
        self._driver.queries.append((query, params))
        # Chỉ lần gọi ĐẦU TIÊN (existence check trong _merge_duplicate_node)
        # trả next_records — các lần sau (redirect/DELETE) trả rỗng, vì code
        # không đọc kết quả của chúng.
        if len(self._driver.queries) == 1:
            return iter(self._driver.next_records)
        return iter([])


class FakeNeo4jDriver:
    def __init__(self, next_records=None):
        self.queries = []
        self.next_records = next_records or []

    def session(self):
        return FakeNeo4jSession(self)


# ---------------------------------------------------------------------------
# _load_alias_map
# ---------------------------------------------------------------------------


def test_load_alias_map_builds_dict_from_rows():
    conn = FakeConn(
        fetchall_result=[
            {"alias_normalized": "golang", "canonical_name": "Go"},
            {"alias_normalized": "ml", "canonical_name": "Machine Learning"},
        ]
    )

    result = tech_dedup._load_alias_map(conn)

    assert result == {"golang": "Go", "ml": "Machine Learning"}


def test_load_alias_map_empty_when_no_rows():
    conn = FakeConn(fetchall_result=[])
    assert tech_dedup._load_alias_map(conn) == {}


# ---------------------------------------------------------------------------
# _save_new_aliases / _save_review_queue
# ---------------------------------------------------------------------------


def test_save_new_aliases_inserts_each_entry_and_commits():
    conn = FakeConn()

    tech_dedup._save_new_aliases(conn, {"k8s": "Kubernetes", "golang": "Go"})

    assert conn.commit_count == 1
    queries = [q for q, _ in conn.cursors[-1].executed]
    assert all("INSERT INTO dp_tech_alias_map" in q for q in queries)
    assert len(conn.cursors[-1].executed) == 2


def test_save_new_aliases_noop_when_empty():
    conn = FakeConn()
    tech_dedup._save_new_aliases(conn, {})
    assert conn.cursors == []
    assert conn.commit_count == 0


def test_save_review_queue_inserts_each_entry():
    conn = FakeConn()

    tech_dedup._save_review_queue(
        conn,
        [
            {"name_a": "Postgres", "name_b": "PostgreSQL", "reasoning": "có thể cùng 1 thứ"},
        ],
    )

    assert conn.commit_count == 1
    query, params = conn.cursors[-1].executed[-1]
    assert "INSERT INTO dp_tech_alias_review_queue" in query
    assert params == ("Postgres", "PostgreSQL", "có thể cùng 1 thứ")


# ---------------------------------------------------------------------------
# _fetch_technology_names
# ---------------------------------------------------------------------------


def test_fetch_technology_names_dedups_and_sorts():
    driver = FakeNeo4jDriver(
        next_records=[
            {"name": "Go"},
            {"name": "React"},
            {"name": "Go"},
            {"name": None},
        ]
    )

    names = tech_dedup._fetch_technology_names(driver)

    assert names == ["Go", "React"]


# ---------------------------------------------------------------------------
# _merge_duplicate_node
# ---------------------------------------------------------------------------


def test_merge_duplicate_node_returns_true_and_redirects_known_relationship_types():
    driver = FakeNeo4jDriver(next_records=[{"c": 1}])  # existence check: node cả 2 đều tồn tại

    merged = tech_dedup._merge_duplicate_node(driver, "Golang", "Go")

    assert merged is True
    # existence check + 4 loại quan hệ incoming + 2 loại outgoing (BELONGS_TO/
    # NEAR_CLUSTER) + 2 chiều RELATED_TO + DETACH DELETE = 10
    assert len(driver.queries) == 10
    for _, params in driver.queries[:-1]:
        assert params == {"canonical_name": "Go", "dup_name": "Golang"}
    last_query, last_params = driver.queries[-1]
    assert "DETACH DELETE" in last_query
    assert last_params == {"dup_name": "Golang"}


def test_merge_duplicate_node_returns_false_when_nothing_matched():
    driver = FakeNeo4jDriver(next_records=[])  # existence check: count=0

    merged = tech_dedup._merge_duplicate_node(driver, "DoesNotExist", "Go")

    assert merged is False
    # chỉ chạy đúng existence check, không chuyển hướng cạnh nào cả
    assert len(driver.queries) == 1


# ---------------------------------------------------------------------------
# _find_exact_duplicate_groups / _merge_duplicate_node_by_id / _dedup_exact_duplicates
# ---------------------------------------------------------------------------


def test_find_exact_duplicate_groups_returns_rows_from_query():
    driver = FakeNeo4jDriver(next_records=[{"name": "TypeScript", "ids": ["1", "2"]}])

    groups = tech_dedup._find_exact_duplicate_groups(driver)

    assert groups == [{"name": "TypeScript", "ids": ["1", "2"]}]


def test_merge_duplicate_node_by_id_returns_true_and_redirects_known_relationship_types():
    driver = FakeNeo4jDriver(next_records=[{"c": 1}])

    merged = tech_dedup._merge_duplicate_node_by_id(driver, "dup-id", "canonical-id")

    assert merged is True
    # existence check + 4 incoming + 2 outgoing + 2 chiều RELATED_TO + DETACH DELETE = 10
    assert len(driver.queries) == 10
    last_query, last_params = driver.queries[-1]
    assert "DETACH DELETE" in last_query
    assert last_params == {"dup_id": "dup-id"}


def test_merge_duplicate_node_by_id_returns_false_when_nothing_matched():
    driver = FakeNeo4jDriver(next_records=[])

    merged = tech_dedup._merge_duplicate_node_by_id(driver, "dup-id", "canonical-id")

    assert merged is False
    assert len(driver.queries) == 1


def test_dedup_exact_duplicates_merges_all_but_first_id_in_each_group(monkeypatch):
    driver = FakeNeo4jDriver()
    groups = [{"name": "TypeScript", "ids": ["2", "1"]}, {"name": "Laravel", "ids": ["5", "3", "4"]}]
    monkeypatch.setattr(tech_dedup, "_find_exact_duplicate_groups", lambda d: groups)

    calls = []

    def fake_merge(d, dup_id, canonical_id):
        calls.append((dup_id, canonical_id))
        return True

    monkeypatch.setattr(tech_dedup, "_merge_duplicate_node_by_id", fake_merge)

    merged_count = tech_dedup._dedup_exact_duplicates(driver)

    assert merged_count == 3
    # canonical = elementId nhỏ nhất trong nhóm (sort theo string) — phần còn lại merge vào đó
    assert calls == [("2", "1"), ("4", "3"), ("5", "3")]


# ---------------------------------------------------------------------------
# _parse_llm_response
# ---------------------------------------------------------------------------


def test_parse_llm_response_plain_json():
    raw = '{"groups": [{"names": ["K8s", "Kubernetes"], "canonical": "Kubernetes", "confidence": "high"}]}'

    groups = tech_dedup._parse_llm_response(raw)

    assert groups == [{"names": ["K8s", "Kubernetes"], "canonical": "Kubernetes", "confidence": "high"}]


def test_parse_llm_response_strips_markdown_fence():
    raw = '```json\n{"groups": []}\n```'
    assert tech_dedup._parse_llm_response(raw) == []


def test_parse_llm_response_missing_groups_key_returns_empty_list():
    assert tech_dedup._parse_llm_response("{}") == []


# ---------------------------------------------------------------------------
# _parse_llm_categories
# ---------------------------------------------------------------------------


def test_parse_llm_categories_plain_json():
    raw = '{"groups": [], "categories": [{"name": "Python", "category": "language"}]}'
    assert tech_dedup._parse_llm_categories(raw) == [{"name": "Python", "category": "language"}]


def test_parse_llm_categories_strips_markdown_fence():
    raw = '```json\n{"categories": [{"name": "Docker", "category": "tool"}]}\n```'
    assert tech_dedup._parse_llm_categories(raw) == [{"name": "Docker", "category": "tool"}]


def test_parse_llm_categories_missing_key_returns_empty_list():
    assert tech_dedup._parse_llm_categories("{}") == []


# ---------------------------------------------------------------------------
# _save_categories
# ---------------------------------------------------------------------------


def test_save_categories_inserts_each_entry_and_commits():
    conn = FakeConn()

    tech_dedup._save_categories(conn, {"Kubernetes": "tool", "Python": "language"})

    assert conn.commit_count == 1
    queries = [q for q, _ in conn.cursors[-1].executed]
    assert all("INSERT INTO dp_tech_category" in q for q in queries)
    assert all("ON CONFLICT (canonical_name) DO UPDATE" in q for q in queries)
    assert len(conn.cursors[-1].executed) == 2


def test_save_categories_noop_when_empty():
    conn = FakeConn()
    tech_dedup._save_categories(conn, {})
    assert conn.cursors == []
    assert conn.commit_count == 0
