from __future__ import annotations

from src.database.utils import database_connection as db


def test_create_constraints_and_indexes_success(importer_factory):
    importer, driver, session, captured = importer_factory()

    assert importer.connect() is True
    assert importer.create_constraints_and_indexes() is True
    assert session.database == "neo4j"
    assert len(session.run_calls) == 6

    statements = [call["query"].strip() for call in session.run_calls]
    assert statements == [
        "CREATE CONSTRAINT IF NOT EXISTS FOR (a:Article) REQUIRE a.title IS UNIQUE",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (t:Technology) REQUIRE t.name IS UNIQUE",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Company) REQUIRE c.name IS UNIQUE",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (j:Job) REQUIRE j.title IS UNIQUE",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (s:Skill) REQUIRE s.name IS UNIQUE",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (p:Person) REQUIRE p.name IS UNIQUE",
    ]


def test_create_constraints_and_indexes_failure(importer_factory, fake_session_class):
    session = fake_session_class(side_effect=RuntimeError("schema error"))
    importer, driver, session, captured = importer_factory(session=session)

    assert importer.connect() is True
    assert importer.create_constraints_and_indexes() is False
    assert len(session.run_calls) == 1
