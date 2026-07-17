from __future__ import annotations

from silver.deduplicator import check_content_duplicate, check_job_duplicate, content_hash


class FakeCursor:
    def __init__(self, fetchone_result=None):
        self.executed = []
        self._fetchone_result = fetchone_result

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, params=None):
        self.executed.append((query, params))

    def fetchone(self):
        return self._fetchone_result


class FakeConn:
    def __init__(self, fetchone_result=None):
        self._fetchone_result = fetchone_result
        self.cursors = []

    def cursor(self):
        cur = FakeCursor(self._fetchone_result)
        self.cursors.append(cur)
        return cur


def test_content_hash_is_case_and_whitespace_insensitive():
    a = content_hash("Python  Developer", "Cần   người biết Python.")
    b = content_hash("python developer", "cần người biết python.")
    assert a == b


def test_content_hash_differs_for_different_content():
    a = content_hash("Python Developer", "Content A")
    b = content_hash("Python Developer", "Content B")
    assert a != b


def test_content_hash_treats_missing_title_or_content_as_empty_string():
    assert content_hash(None, "hello") == content_hash("", "hello")


def test_check_content_duplicate_returns_existing_id_when_row_found():
    conn = FakeConn(fetchone_result={"id": "existing-article-id"})
    result = check_content_duplicate(conn, "somehash", "current-id")
    assert result == "existing-article-id"
    query, params = conn.cursors[0].executed[0]
    assert "dp_processed_articles" in query
    assert params == ("somehash", "current-id")


def test_check_content_duplicate_returns_none_when_no_row_found():
    conn = FakeConn(fetchone_result=None)
    assert check_content_duplicate(conn, "somehash", "current-id") is None


def test_check_job_duplicate_returns_true_when_row_found():
    conn = FakeConn(fetchone_result={"exists": 1})
    assert check_job_duplicate(conn, "somehash", "current-id") is True
    query, params = conn.cursors[0].executed[0]
    assert "dp_processed_jobs" in query
    assert params == ("somehash", "current-id")


def test_check_job_duplicate_returns_false_when_no_row_found():
    conn = FakeConn(fetchone_result=None)
    assert check_job_duplicate(conn, "somehash", "current-id") is False
