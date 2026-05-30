from __future__ import annotations

import sys
from pathlib import Path

from neo4j.exceptions import ServiceUnavailable

ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = ROOT / "src"
UTILS_DIR = SRC_DIR / "database" / "utils"

if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from src.database.utils import database_connection as db


def test_connect_success(importer_factory):
    importer, driver, session, captured = importer_factory()

    assert importer.connect() is True
    assert driver.verify_called is True
    assert captured["uri"] == "neo4j+s://example.databases.neo4j.io"
    assert captured["auth"] == ("neo4j", "secret")


def test_connect_failure_when_service_unavailable(monkeypatch, importer_factory):
    def raise_service_unavailable(uri, auth=None):
        raise ServiceUnavailable("Neo4j unavailable")

    monkeypatch.setattr(db.GraphDatabase, "driver", raise_service_unavailable)
    importer = db.Neo4jJobImporter("neo4j+s://example.databases.neo4j.io", "neo4j", "secret")

    assert importer.connect() is False


def test_connect_failure_when_driver_raises_generic_error(monkeypatch):
    def raise_error(uri, auth=None):
        raise RuntimeError("boom")

    monkeypatch.setattr(db.GraphDatabase, "driver", raise_error)
    importer = db.Neo4jJobImporter("neo4j+s://example.databases.neo4j.io", "neo4j", "secret")

    assert importer.connect() is False


def test_disconnect_closes_driver(importer_factory):
    importer, driver, session, captured = importer_factory()

    assert importer.connect() is True
    importer.disconnect()

    assert driver.closed is True
