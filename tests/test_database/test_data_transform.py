from __future__ import annotations

import json
import sys
import types
from datetime import datetime
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
UTILS_DIR = ROOT / "src" / "database" / "utils"

if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from src.database.utils import schema_define as schema_module

shim = types.ModuleType("database_connection")
shim.Article = schema_module.Article
shim.Job = schema_module.Job
sys.modules.setdefault("database_connection", shim)

from src.database.utils.data_transform import DataTransformer


@pytest.fixture
def transformer():
    return DataTransformer()


def test_detect_tech_category_covers_known_and_unknown_categories(transformer):
    assert transformer._detect_tech_category("React Native") == "Frontend"
    assert transformer._detect_tech_category("Python") == "Backend"
    assert transformer._detect_tech_category("Kubernetes") == "Cloud"
    assert transformer._detect_tech_category("Flutter") == "Mobile"
    assert transformer._detect_tech_category("Random Tool") == "Other"


def test_calculate_sentiment_handles_positive_negative_and_neutral_text(transformer):
    assert transformer._calculate_sentiment("Sản phẩm tăng mạnh, cải thiện hiệu quả và rất tốt") == 1.0
    assert transformer._calculate_sentiment("Hệ thống lỗi, thất bại và nhiều rủi ro") == -1.0
    assert transformer._calculate_sentiment("Nội dung trung tính không có tín hiệu rõ ràng") == 0.0


def test_extract_role_from_context_detects_priority_order(transformer):
    assert transformer._extract_role_from_context("Nguyễn Văn A", "Ông là CEO của công ty") == "CEO"
    assert transformer._extract_role_from_context("Nguyễn Văn B", "Bà là CTO phụ trách kỹ thuật") == "CTO"
    assert transformer._extract_role_from_context("Nguyễn Văn C", "Anh là nhà sáng lập của startup") == "Founder"
    assert transformer._extract_role_from_context("Nguyễn Văn D", "Anh làm engineer nhiều năm") == "Engineer"
    assert transformer._extract_role_from_context("Nguyễn Văn E", "Chị là nhà nghiên cứu AI") == "Researcher"
    assert transformer._extract_role_from_context("Nguyễn Văn F", "Anh đang quản lý đội ngũ") == "Manager"
    assert transformer._extract_role_from_context("Nguyễn Văn G", "Không có tín hiệu vai trò cụ thể") == "Unknown"


def test_transform_article_creates_nodes_and_skills(transformer):
    raw_article = {
        "title": "OpenAI và FPT đẩy mạnh React Native",
        "description": "Bài viết nói về React Native, OpenAI và Nguyễn Văn A là CTO.",
        "created_at": "2026-04-11T08:30:00",
        "source_platform": "VN-Express",
        "entities": {
            "technologies": ["React Native", "Python"],
            "organizations": ["OpenAI", "FPT"],
            "persons": ["Nguyễn Văn A"],
        },
    }

    article, extracted = transformer.transform_article(raw_article)

    assert article.title == "OpenAI và FPT đẩy mạnh React Native"
    assert article.source == "VN-Express"
    assert article.published_date == datetime(2026, 4, 11, 8, 30, 0)
    assert article.sentiment_score == 0.0

    assert [tech.name for tech in extracted["technologies"]] == ["React Native", "Python"]
    assert [tech.category for tech in extracted["technologies"]] == ["Frontend", "Backend"]
    assert [company.name for company in extracted["companies"]] == ["OpenAI", "FPT"]
    assert [person.role for person in extracted["persons"]] == ["CTO"]
    assert [skill.name for skill in extracted["skills"]] == ["React Native", "Python"]

    assert len(transformer.articles) == 1
    assert len(transformer.technologies) == 2
    assert len(transformer.companies) == 2
    assert len(transformer.persons) == 1
    assert len(transformer.skills) == 2


def test_transform_article_falls_back_to_now_for_invalid_date(monkeypatch, transformer):
    class FixedDatetime(datetime):
        @classmethod
        def fromisoformat(cls, value):
            raise ValueError("invalid isoformat")

        @classmethod
        def now(cls):
            return cls(2026, 4, 11, 12, 0, 0)

    from src.database.utils import data_transform as data_transform_module

    monkeypatch.setattr(data_transform_module, "datetime", FixedDatetime)

    article, extracted = transformer.transform_article(
        {
            "title": "Bài viết lỗi ngày",
            "description": "Nội dung có lỗi nhưng vẫn xử lý được.",
            "created_at": "not-a-date",
            "source_platform": "Dân Trí",
            "entities": {},
        }
    )

    assert article.published_date == FixedDatetime(2026, 4, 11, 12, 0, 0)
    assert extracted == {"technologies": [], "companies": [], "persons": [], "skills": []}


def test_batch_transform_skips_empty_entries_and_counts_nodes(tmp_path, transformer):
    input_file = tmp_path / "raw.json"
    input_file.write_text(
        json.dumps(
            {
                "post_detail": [
                    None,
                    {
                        "title": "React và OpenAI",
                        "description": "React tăng mạnh, OpenAI cải thiện hiệu quả.",
                        "created_at": "2026-04-11T08:30:00",
                        "source_platform": "VN-Express",
                        "entities": {
                            "technologies": ["React"],
                            "organizations": ["OpenAI"],
                            "persons": ["Nguyễn Văn A"],
                        },
                    },
                    {
                        "title": "Kubernetes và FPT",
                        "description": "Kubernetes giúp hệ thống mạnh mẽ hơn.",
                        "created_at": "2026-04-10T08:30:00",
                        "source_platform": "Dân Trí",
                        "entities": {
                            "technologies": ["Kubernetes"],
                            "organizations": ["FPT"],
                            "persons": ["Trần B"],
                        },
                    },
                ]
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    stats = transformer.batch_transform(str(input_file))

    assert stats == {
        "articles": 2,
        "technologies": 2,
        "companies": 2,
        "persons": 2,
        "skills": 2,
    }
    assert len(transformer.articles) == 2
    assert len(transformer.technologies) == 2
    assert len(transformer.companies) == 2
    assert len(transformer.persons) == 2
    assert len(transformer.skills) == 2


def test_export_to_json_writes_truncated_nodes_file(tmp_path, transformer):
    transformer.transform_article(
        {
            "title": "Bài viết xuất JSON",
            "description": "x" * 250,
            "created_at": "2026-04-11T08:30:00",
            "source_platform": "VN-Express",
            "entities": {
                "technologies": ["Python"],
                "organizations": ["OpenAI"],
                "persons": ["Nguyễn Văn A"],
            },
        }
    )

    transformer.export_to_json(str(tmp_path))

    output_file = tmp_path / "nodes.json"
    assert output_file.exists()

    payload = json.loads(output_file.read_text(encoding="utf-8"))
    assert payload["articles"][0]["content"] == "x" * 200
    assert payload["technologies"][0]["name"] == "Python"
    assert payload["companies"][0]["name"] == "OpenAI"
    assert payload["persons"][0]["name"] == "Nguyễn Văn A"
    assert payload["skills"][0]["name"] == "Python"


def test_get_summary_reflects_current_counts(transformer):
    transformer.transform_article(
        {
            "title": "Summary test",
            "description": "Tăng hiệu quả.",
            "created_at": "2026-04-11T08:30:00",
            "source_platform": "VN-Express",
            "entities": {
                "technologies": ["Python"],
                "organizations": ["FPT"],
                "persons": ["Nguyễn Văn A"],
            },
        }
    )

    summary = transformer.get_summary()

    assert "Articles:      1" in summary
    assert "Technologies: 1" in summary
    assert "Companies:     1" in summary
    assert "Persons:       1" in summary
    assert "Skills:        1" in summary
