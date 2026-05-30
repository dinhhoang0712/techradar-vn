from __future__ import annotations

from datetime import datetime

from src.database.utils import database_connection as db
from src.database.utils import schema_define as schema


def test_import_articles_success(importer_factory):
    importer, driver, session, captured = importer_factory()
    articles = [
        schema.Article("A1", "content 1", "VN-Express", datetime(2026, 4, 11), 0.2),
        schema.Article("A2", "content 2", "Dân Trí", datetime(2026, 4, 10), -0.1),
    ]

    assert importer.connect() is True
    assert importer.import_articles(articles) == 2
    assert len(session.run_calls) == 2
    assert "MERGE (a:Article" in session.run_calls[0]["query"]
    assert session.run_calls[0]["parameters"]["published_date"] == "2026-04-11T00:00:00"


def test_import_jobs_formats_date_and_handles_none(importer_factory):
    importer, driver, session, captured = importer_factory()
    jobs = [
        db.JobNode(
            title="Senior Python Engineer",
            salary_min=1200,
            salary_max=2400,
            level="Senior",
            source_url="https://example.com/job-1",
            company_name="FPT",
            posted_date=datetime(2026, 4, 11),
        ),
        db.JobNode(
            title="Data Analyst",
            company_name="",
            posted_date=None,
        ),
    ]

    assert importer.connect() is True
    assert importer.import_jobs(jobs) == 2
    assert session.run_calls[0]["parameters"]["posted_date"] == "2026-04-11T00:00:00"
    assert session.run_calls[1]["parameters"]["posted_date"] is None


def test_import_node_lists_cover_helper_variants(importer_factory):
    importer, driver, session, captured = importer_factory()
    technologies = [db.TechNode("Python", "Backend", "Python language", 0.9)]
    companies = [db.CompanyNode("FPT", "Technology", "Enterprise", "Hanoi", 4.5)]
    skills = [db.SkillNode("Python", "Programming", 0.8)]
    jobs = [db.JobNode("Python Developer", company_name="FPT")]

    assert importer.connect() is True
    assert importer.import_technologies_list(technologies) == 1
    assert importer.import_companies_list(companies) == 1
    assert importer.import_skills_list(skills) == 1
    assert importer.import_jobs_list(jobs) == 1
    assert len(session.run_calls) == 4


def test_import_methods_return_zero_on_failure(importer_factory, fake_session_class):
    session = fake_session_class(side_effect=RuntimeError("insert error"))
    importer, driver, session, captured = importer_factory(session=session)
    articles = [schema.Article("A1", "content", "VN-Express", datetime(2026, 4, 11), 0.0)]

    assert importer.connect() is True
    assert importer.import_articles(articles) == 0
    assert importer.import_jobs([db.JobNode("J1")]) == 0
    assert importer.import_technologies([schema.Technology("Python", "Backend")]) == 0
    assert importer.import_companies([schema.Company("FPT", "Technology")]) == 0
    assert importer.import_persons([schema.Person("Sam Altman", "CEO")]) == 0
    assert importer.import_skills([schema.Skill("Python", "Programming")]) == 0
