from __future__ import annotations

import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = ROOT / "src"
UTILS_DIR = SRC_DIR / "database" / "utils"

if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from src.database.utils import database_connection as db
from src.database.utils import schema_define as schema
from dataclasses import dataclass

@dataclass
class PatchedJobNode:
    title: str
    description: str = ""
    requirement: str = ""
    benefit: str = ""
    salary: str = ""
    due_date: __import__("datetime").datetime = None
    source_url: str = ""
    salary_min: int = 0
    salary_max: int = 0
    level: str = "mid"
    company_name: str = ""
    posted_date: __import__("datetime").datetime = None

db.JobNode = PatchedJobNode


class FakeResult:
    def __init__(self, record=None):
        self.record = record

    def single(self):
        return self.record


class FakeSession:
    def __init__(self, results=None, side_effect=None):
        self.results = list(results or [])
        self.side_effect = side_effect
        self.run_calls = []
        self.database = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, parameters=None):
        self.run_calls.append({"query": query, "parameters": parameters})
        if self.side_effect is not None:
            raise self.side_effect
        if self.results:
            value = self.results.pop(0)
            if isinstance(value, FakeResult):
                return value
            return FakeResult(value)
        return FakeResult()


class FakeDriver:
    def __init__(self, session):
        self.session_obj = session
        self.verify_called = False
        self.closed = False
        self.session_calls = []

    def verify_connectivity(self):
        self.verify_called = True

    def close(self):
        self.closed = True

    def session(self, database=None):
        self.session_calls.append(database)
        self.session_obj.database = database
        return self.session_obj


@pytest.fixture
def fake_session_class():
    return FakeSession


@pytest.fixture
def importer_factory(monkeypatch):
    def make(session=None, uri="neo4j+s://example.databases.neo4j.io", username="neo4j", password="secret", database="neo4j"):
        session = session or FakeSession()
        driver = FakeDriver(session)
        captured = {}

        def fake_driver_factory(actual_uri, auth=None):
            captured["uri"] = actual_uri
            captured["auth"] = auth
            return driver

        monkeypatch.setattr(db.GraphDatabase, "driver", fake_driver_factory)
        importer = db.Neo4jJobImporter(uri, username, password, database=database)
        return importer, driver, session, captured

    return make


@pytest.fixture
def article_with_matches():
    return schema.Article(
        title="Python và OpenAI tạo đột phá",
        content="OpenAI hợp tác với FPT và Python được nhắc tới trong bài viết.",
        source="VN-Express",
        published_date=__import__("datetime").datetime(2026, 4, 11),
        sentiment_score=0.8,
    )


@pytest.fixture
def article_without_matches():
    return schema.Article(
        title="Tin tức tổng hợp",
        content="Nội dung không liên quan tới công nghệ hoặc công ty cụ thể.",
        source="Dân Trí",
        published_date=__import__("datetime").datetime(2026, 4, 11),
        sentiment_score=0.0,
    )
