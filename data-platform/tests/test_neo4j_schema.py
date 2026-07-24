from __future__ import annotations

from common.neo4j_schema import _CONSTRAINTS, ensure_constraints


class FakeSession:
    def __init__(self, driver, fail_on=()):
        self._driver = driver
        self._fail_on = fail_on

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, params=None):
        self._driver.queries.append(query)
        if any(name in query for name in self._fail_on):
            raise RuntimeError("constraint creation failed (existing violation)")


class FakeDriver:
    def __init__(self, fail_on=()):
        self.queries = []
        self._fail_on = fail_on

    def session(self):
        return FakeSession(self, self._fail_on)


def test_ensure_constraints_runs_one_create_statement_per_constraint():
    driver = FakeDriver()

    ensure_constraints(driver)

    assert len(driver.queries) == len(_CONSTRAINTS)
    for query in driver.queries:
        assert "CREATE CONSTRAINT" in query
        assert "IF NOT EXISTS" in query
        assert "IS UNIQUE" in query


def test_ensure_constraints_does_not_raise_when_one_constraint_fails():
    driver = FakeDriver(fail_on=["technology_name_unique"])

    ensure_constraints(driver)  # không raise dù 1 constraint fail (vd dữ liệu còn vi phạm)

    assert len(driver.queries) == len(_CONSTRAINTS)
