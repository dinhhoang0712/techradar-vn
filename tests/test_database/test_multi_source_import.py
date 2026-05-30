from __future__ import annotations

import json
import sys
from datetime import datetime
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
UTILS_DIR = ROOT / "src" / "database" / "utils"

if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from src.database.utils import database_connection as db_module
from src.database.utils import neo4j_config as neo4j_config_module

sys.modules["database_connection"] = db_module
sys.modules["neo4j_config"] = neo4j_config_module
from src.database.utils import import_multi_source as module

RelationshipBuilder = module.RelationshipBuilder


@pytest.fixture
def builder():
    instance = RelationshipBuilder()
    instance._tech_category_map = {
        "AI": ["AI", "Machine Learning", "GPT", "LLM"],
        "Cloud": ["AWS", "Azure", "Kubernetes"],
        "Frontend": ["React", "Vue", "Angular"],
        "Backend": ["Python", "Java", "Node.js"],
        "Mobile": ["Flutter", "Android", "iOS"],
    }
    return instance


def test_parse_salary_covers_common_and_edge_cases(builder):
    assert builder._parse_salary("10 - 20 triệu") == (10, 20)
    assert builder._parse_salary("1000 USD") == (None, None)
    assert builder._parse_salary("Lên tới 50 triệu") == (None, None)
    assert builder._parse_salary("Thoả thuận") == (None, None)


def test_extract_job_level_detects_levels_from_titles(builder):
    assert builder._extract_job_level("Tuyển dụng Thực tập sinh Python") == "fresher"
    assert builder._extract_job_level("Tuyển dụng Nhân viên mới") == "junior"
    assert builder._extract_job_level("Kỹ sư phần mềm Mid-level") == "mid"
    assert builder._extract_job_level("Senior Backend Engineer") == "senior"
    assert builder._extract_job_level("Tuyển Kỹ sư Team Lead") == "lead"
    assert builder._extract_job_level("Tuyển Kỹ sư phần mềm") == "mid"


def test_parse_date_supports_all_supported_formats(monkeypatch, builder):
    class FixedDatetime(datetime):
        @classmethod
        def now(cls):
            return cls(2026, 4, 11, 12, 0, 0)

    monkeypatch.setattr(module, "datetime", FixedDatetime)

    assert builder._parse_date("11/04/2026") == FixedDatetime(2026, 4, 11)
    assert builder._parse_date("2026-04-11") == FixedDatetime(2026, 4, 11)
    assert builder._parse_date("2026-04-11 08:30:00") == FixedDatetime(2026, 4, 11, 8, 30, 0)
    assert builder._parse_date("11/04/2026 08:30:00") == FixedDatetime(2026, 4, 11, 8, 30, 0)
    assert builder._parse_date("invalid date") == FixedDatetime(2026, 4, 11, 12, 0, 0)


def test_import_news_data_extracts_entities_and_deduplicates(tmp_path, builder):
    input_file = tmp_path / "news.json"
    input_file.write_text(
        json.dumps(
            {
                "source_platform": "VN-Express",
                "post_detail": [
                    None,
                    {
                        "title": "AI và FPT tăng trưởng",
                        "description": "OpenAI, FPT, và Nguyễn Văn A là CEO. React cũng được nhắc tới.",
                        "created_at": "11/04/2026 08:30:00",
                        "entities": {
                            "TECH": ["OpenAI", "React"],
                            "SKILL/TECH": ["React", "Python"],
                            "ORG": ["FPT", "FPT"],
                            "JOB_ROLE": ["Senior Backend Engineer", "Senior Backend Engineer"],
                        },
                    },
                ]
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    stats = builder.import_news_data(str(input_file), "VN-Express")

    assert stats == {
        "articles": 1,
        "technologies": 3,
        "companies": 1,
        "job_roles": 1,
        "skills": 2,
        "total_articles": 2,
    }
    assert len(builder.articles) == 1
    assert len(builder.technologies) == 3
    assert len(builder.companies) == 1
    assert len(builder.jobs) == 1
    assert len(builder.skills) == 2
    assert builder.jobs[0].level == "senior"
    assert builder._article_company_map == {"AI và FPT tăng trưởng": "FPT"}


def test_import_topcv_data_extracts_jobs_and_skills(tmp_path, builder):
    input_file = tmp_path / "topcv.json"
    input_file.write_text(
        json.dumps(
            {
                "source_platform": "TopCV",
                "post_detail": [
                    {
                        "job": {
                            "title": "Junior Python Developer",
                            "description": "Cần Python, React và công ty FPT.",
                            "due_date": "2026-04-10",
                            "salary": "10 - 20 triệu",
                            "source_url": "https://topcv.vn/..."
                        },
                        "company": {"name": "FPT", "field": "Tech", "location": "Hanoi"},
                        "skills": ["Python", "React"],
                        "technologies": ["Python", "React"]
                    },
                    {
                        "job": {
                            "title": "Senior Backend Engineer",
                            "description": "Lương thoả thuận, yêu cầu AWS.",
                            "due_date": "2026-04-11",
                            "salary": "Thoả thuận"
                        },
                        "company": {"name": "OpenAI"},
                        "skills": ["AWS"],
                        "technologies": ["AWS"]
                    },
                ]
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    stats = builder.import_topcv_data(str(input_file))

    assert stats == {
        "job_postings": 2,
        "technologies": 3,
        "job_roles": 2,
        "skills": 3,
        "companies": 2,
    }
    assert len(builder.jobs) == 2
    assert builder.jobs[0].level == "mid"
    assert builder.jobs[0].salary_min == 0
    assert builder.jobs[0].salary_max == 0
    assert builder.jobs[1].level == "mid"
    assert len(builder.technologies) == 3
    assert len(builder.skills) == 3
    assert builder._job_company_map == {
        "Junior Python Developer": "FPT",
        "Senior Backend Engineer": "OpenAI",
    }


def test_import_methods_handle_empty_or_invalid_entries(builder, tmp_path):
    input_file = tmp_path / "broken.json"
    input_file.write_text(
        json.dumps(
            {
                "post_detail": [
                    {},
                    None,
                    {
                        "title": "Missing entities",
                        "description": "No entities available.",
                        "created_at": "invalid",
                        "entities": {},
                    },
                ]
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    stats = builder.import_news_data(str(input_file), "DT")

    assert stats["articles"] == 1
    assert stats["technologies"] == 0
    assert stats["companies"] == 0
    assert stats["job_roles"] == 0
    assert stats["skills"] == 0
