from __future__ import annotations

import os
from pathlib import Path

import pytest
from dotenv import load_dotenv
from neo4j import GraphDatabase


ROOT = Path(__file__).resolve().parents[2]
load_dotenv(ROOT / ".env")


@pytest.mark.integration
def test_live_neo4j_connection_and_rollbacked_write():
    uri = os.getenv("NEO4J_URI")
    username = os.getenv("NEO4J_USERNAME")
    password = os.getenv("NEO4J_PASSWORD")
    database = os.getenv("NEO4J_DATABASE", "neo4j")

    if not uri or "your-instance" in uri or not username or not password or "your-password" in password:
        pytest.skip("Real Neo4j credentials are not configured")

    driver = GraphDatabase.driver(uri, auth=(username, password))

    try:
        driver.verify_connectivity()

        with driver.session(database=database) as session:
            assert session.run("RETURN 1 AS value").single()["value"] == 1

            tx = session.begin_transaction()
            try:
                record = tx.run(
                    "CREATE (n:__CopilotIntegrationTest {id: $id}) RETURN n.id AS id",
                    id="live-check",
                ).single()
                assert record["id"] == "live-check"
            finally:
                tx.rollback()
    finally:
        driver.close()
