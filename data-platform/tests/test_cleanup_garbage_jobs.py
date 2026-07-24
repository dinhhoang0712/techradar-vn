from __future__ import annotations

import gold.cleanup_garbage_jobs as cleanup_garbage_jobs

# ---------------------------------------------------------------------------
# Fakes — theo đúng convention của test_kg_health_audit.py (Neo4j) và
# test_silver_processor.py (Postgres), gộp lại vì script này chạm cả 2.
# ---------------------------------------------------------------------------


class FakeResult:
    def __init__(self, rows):
        self._rows = rows

    def data(self):
        return self._rows


class FakeSession:
    def __init__(self, rows, run_calls):
        self._rows = rows
        self._run_calls = run_calls

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, **params):
        self._run_calls.append((query, params))
        if "MATCH (j:Job)" in query and "DETACH DELETE" not in query:
            return FakeResult(self._rows)
        return FakeResult([])


class FakeDriver:
    def __init__(self, rows):
        self._rows = rows
        self.run_calls = []
        self.closed = False

    def session(self):
        return FakeSession(self._rows, self.run_calls)

    def close(self):
        self.closed = True


class FakeCursor:
    def __init__(self, rowcount):
        self.executed = []
        self.rowcount = rowcount

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, params=None):
        self.executed.append((query, params))


class FakeConn:
    def __init__(self, rowcount=0):
        self._rowcount = rowcount
        self.cursors = []
        self.commit_count = 0
        self.closed = False

    def cursor(self):
        cur = FakeCursor(self._rowcount)
        self.cursors.append(cur)
        return cur

    def commit(self):
        self.commit_count += 1

    def close(self):
        self.closed = True


def _patch(monkeypatch, driver, conn):
    monkeypatch.setattr(cleanup_garbage_jobs, "get_neo4j_driver", lambda settings: driver)
    monkeypatch.setattr(cleanup_garbage_jobs, "get_pg_conn", lambda settings: conn)


def test_run_deletes_neo4j_nodes_and_marks_postgres_invalid(monkeypatch):
    driver = FakeDriver([{"id": "abc123", "title": "Sorry, you have been blocked"}, {"id": "def456", "title": "www.topcv.vn"}])
    conn = FakeConn(rowcount=2)
    _patch(monkeypatch, driver, conn)

    result = cleanup_garbage_jobs.run(settings=object())

    assert result == {"deleted_from_neo4j": 2, "marked_invalid_in_postgres": 2}
    delete_query, delete_params = driver.run_calls[-1]
    assert "DETACH DELETE" in delete_query
    assert delete_params == {"ids": ["abc123", "def456"]}
    pg_query, pg_params = conn.cursors[-1].executed[-1]
    assert "UPDATE dp_processed_jobs" in pg_query
    assert pg_params == (["abc123", "def456"],)
    assert conn.commit_count == 1
    assert driver.closed is True
    assert conn.closed is True


def test_run_is_idempotent_when_no_garbage_found(monkeypatch):
    driver = FakeDriver([])
    conn = FakeConn(rowcount=0)
    _patch(monkeypatch, driver, conn)

    result = cleanup_garbage_jobs.run(settings=object())

    assert result == {"deleted_from_neo4j": 0, "marked_invalid_in_postgres": 0}
    assert conn.cursors == []  # không chạm Postgres nếu không có gì để dọn
    assert driver.closed is True
    assert conn.closed is True
