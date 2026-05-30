from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UTILS_DIR = ROOT / "src" / "database" / "utils"

if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from src.database.utils import neo4j_config as neo4j_config_module

sys.modules["neo4j_config"] = neo4j_config_module

from src.database.utils import create_relationships as module


class FakeResult:
    def __init__(self, record):
        self.record = record

    def single(self):
        return self.record


class FakeSession:
    def __init__(self):
        self.run_calls = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query):
        self.run_calls.append(query)
        if "RETURN count(r) as count" in query:
            return FakeResult({"count": 42})
        return FakeResult({"rel_count": 3})


class FakeDriver:
    def __init__(self):
        self.session_obj = FakeSession()
        self.closed = False
        self.session_calls = []

    def session(self, database=None):
        self.session_calls.append(database)
        return self.session_obj

    def close(self):
        self.closed = True


def test_create_relationships_runs_all_queries_and_closes_driver(monkeypatch):
    fake_driver = FakeDriver()

    monkeypatch.setattr(module.GraphDatabase, "driver", lambda *args, **kwargs: fake_driver)

    module.create_relationships()

    assert fake_driver.closed is True
    assert fake_driver.session_calls == [neo4j_config_module.NEO4J_DATABASE]
    assert len(fake_driver.session_obj.run_calls) == 7
    assert any("HIRES_FOR" in query for query in fake_driver.session_obj.run_calls)
    assert any("REQUIRES {is_mandatory: true, frequency: 1}" in query for query in fake_driver.session_obj.run_calls)
    assert any("USES {frequency: 1}" in query for query in fake_driver.session_obj.run_calls)
    assert any("WORKS_AT" in query for query in fake_driver.session_obj.run_calls)
    assert any("MENTIONS" in query for query in fake_driver.session_obj.run_calls)


def test_verify_relationships_runs_all_counts_and_closes_driver(monkeypatch):
    fake_driver = FakeDriver()

    monkeypatch.setattr(module.GraphDatabase, "driver", lambda *args, **kwargs: fake_driver)

    module.verify_relationships()

    assert fake_driver.closed is True
    assert fake_driver.session_calls == [neo4j_config_module.NEO4J_DATABASE]
    assert len(fake_driver.session_obj.run_calls) == 5
    assert all("count(r) as count" in query for query in fake_driver.session_obj.run_calls)
