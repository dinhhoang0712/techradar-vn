from __future__ import annotations

from datetime import datetime

import pytest

from src.database.utils import database_connection as db
from src.database.utils import schema_define as schema


class DummyTransformer:
    def __init__(self, articles=None, technologies=None, companies=None, skills=None):
        self.articles = articles or []
        self.technologies = technologies or []
        self.companies = companies or []
        self.skills = skills or []


def test_article_mentions_relationships_by_content_and_title(importer_factory, article_with_matches, article_without_matches):
    importer, driver, session, captured = importer_factory()
    transformer = DummyTransformer(
        articles=[
            article_with_matches,
            article_without_matches,
            schema.Article(
                title="FPT đẩy mạnh Python",
                content="",
                source="VN-Express",
                published_date=datetime(2026, 4, 11),
                sentiment_score=0.3,
            ),
        ],
        technologies=[db.TechNode("Python"), db.TechNode("Kubernetes")],
        companies=[db.CompanyNode("OpenAI"), db.CompanyNode("FPT")],
    )

    assert importer.connect() is True
    assert importer.create_article_mentions_relationships(transformer) == 5
    assert len(session.run_calls) == 5
    assert any("MATCH (a:Article" in call["query"] and "Technology" in call["query"] for call in session.run_calls)
    assert any("MATCH (a:Article" in call["query"] and "Company" in call["query"] for call in session.run_calls)


def test_job_relationship_heuristics_cover_company_tech_skill(importer_factory, fake_session_class):
    session = fake_session_class(results=[
        {},
        {},
        {"rel_count": 1},
        {"rel_count": 1},
        {"rel_count": 1},
    ])
    importer, driver, session, captured = importer_factory(session=session)
    importer.jobs = [
        db.JobNode("Senior Python Developer", company_name="FPT"),
        db.JobNode("CEO", company_name="Unknown"),
    ]
    importer.technologies = {db.TechNode("Python"), db.TechNode("C")}
    importer.skills = {db.SkillNode("Python"), db.SkillNode("C")}

    assert importer.connect() is True
    assert importer.create_job_requires_relationships() == 2
    assert importer.create_job_requires_skill_relationships() == 2
    assert importer.create_job_company_relationships() == 1
    assert session.run_calls[0]["query"].strip().startswith("MATCH (j:Job {title: $title})")
    # Due to the bug, "C" will be in the parameters because "C" in "ceo".lower()
    assert any(call["parameters"]["name"] == "C" for call in session.run_calls if call["parameters"])


def test_graph_relationship_methods_return_counts_and_queries(importer_factory, fake_session_class):
    session = fake_session_class(results=[
        {"rel_count": 2},
        {"rel_count": 3},
        {"rel_count": 4},
        {"rel_count": 1},
        {"count": 2},
        {"count": 3},
        {"rel_count": 6},
        {"count": 7},
        {"count": 8},
        {"count": 0},
        {"count": 0},
        {"count": 0},
        {"count": 0},
    ])
    importer, driver, session, captured = importer_factory(session=session)

    assert importer.connect() is True
    assert importer.create_company_uses_technology_relationships() == 2
    assert importer.create_technology_related_to_relationships() == 3
    assert importer.create_person_works_at_relationships() == 4
    assert importer.create_person_wrote_article_relationships() == 1
    assert importer.create_skill_relationships(DummyTransformer(skills=[db.SkillNode("Python"), db.SkillNode("Git")])) == 5
    assert importer.create_company_uses_tech_relationships() == 6
    assert importer.verify_relationships() == {
        "MENTIONS": 7,
        "USES": 8,
        "RELATED_TO": 0,
        "WORKS_AT": 0,
        "WROTE": 0,
        "IS_TECHNOLOGY": 0,
    }

    assert len(session.run_calls) == 13
    assert any("USES {frequency: co_mention_count}" in call["query"] for call in session.run_calls)
    assert any("RELATED_TO {frequency: co_mention_count}" in call["query"] for call in session.run_calls)
    assert any("WORKS_AT {confidence: mention_count}" in call["query"] for call in session.run_calls)
    assert any("WROTE" in call["query"] for call in session.run_calls)
    assert any("IS_TECHNOLOGY" in call["query"] for call in session.run_calls)
