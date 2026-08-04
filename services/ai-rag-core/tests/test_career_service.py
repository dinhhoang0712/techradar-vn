import uuid

import pytest

import app.services.career_service as career_service
from app.api.schemas import CareerRequest


@pytest.mark.asyncio
async def test_get_user_profile_defaults_returns_skills_level_and_job_role(mock_db):
    mock_db.execute.return_value.mappings.return_value.first.return_value = {
        "preferences_json": {"interested_techs": ["Python", "Docker"]},
        "current_level": "Middle",
        "job_role": "Backend Engineer",
    }

    skills, level, job_role = await career_service._get_user_profile_defaults(uuid.uuid4(), mock_db)

    assert skills == ["Python", "Docker"]
    assert level == "Middle"
    assert job_role == "Backend Engineer"


@pytest.mark.asyncio
async def test_get_user_profile_defaults_returns_empty_when_no_row(mock_db):
    mock_db.execute.return_value.mappings.return_value.first.return_value = None

    skills, level, job_role = await career_service._get_user_profile_defaults(uuid.uuid4(), mock_db)

    assert skills == []
    assert level is None
    assert job_role is None


@pytest.mark.asyncio
async def test_role_required_skills_uses_leveled_result_when_enough_matches(monkeypatch):
    leveled = [{"skill": f"Skill{i}"} for i in range(career_service._MIN_LEVELED_SKILLS)]
    calls = []

    async def fake_run_query(cypher, params=None):
        calls.append(params)
        return leveled

    monkeypatch.setattr(career_service, "run_query", fake_run_query)

    skills = await career_service._neo4j_role_required_skills("Backend Developer", "Senior")

    assert skills == [f"Skill{i}" for i in range(career_service._MIN_LEVELED_SKILLS)]
    assert len(calls) == 1
    assert calls[0]["level"] == "Senior"


@pytest.mark.asyncio
async def test_role_required_skills_falls_back_to_unfiltered_when_leveled_result_too_sparse(monkeypatch):
    call_count = 0

    async def fake_run_query(cypher, params=None):
        nonlocal call_count
        call_count += 1
        if "level" in params:
            return [{"skill": "OnlyOne"}]  # below _MIN_LEVELED_SKILLS
        return [{"skill": "Python"}, {"skill": "Docker"}]

    monkeypatch.setattr(career_service, "run_query", fake_run_query)

    skills = await career_service._neo4j_role_required_skills("Backend Developer", "Senior")

    assert skills == ["Python", "Docker"]
    assert call_count == 2


@pytest.mark.asyncio
async def test_role_required_skills_skips_leveled_query_when_no_target_level(monkeypatch):
    calls = []

    async def fake_run_query(cypher, params=None):
        calls.append(params)
        return [{"skill": "Python"}]

    monkeypatch.setattr(career_service, "run_query", fake_run_query)

    skills = await career_service._neo4j_role_required_skills("Backend Developer")

    assert skills == ["Python"]
    assert len(calls) == 1
    assert "level" not in calls[0]


async def _fake_generate(messages):
    return "roadmap markdown"


async def _fake_sql_analytics_search(techs, months=3):
    return []


@pytest.mark.asyncio
async def test_handle_resolves_current_level_from_profile_when_not_in_request(monkeypatch, mock_db):
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)
    monkeypatch.setattr(career_service, "run_query", lambda cypher, params=None: _empty_rows())
    mock_db.execute.return_value.mappings.return_value.first.return_value = {
        "preferences_json": {"interested_techs": ["Python"]},
        "current_level": "Junior",
        "job_role": "Backend Engineer",
    }

    req = CareerRequest(user_id=uuid.uuid4(), target_role="Backend Developer", current_skills=[])
    res = await career_service.handle(req, mock_db)

    assert res.current_level == "Junior"
    assert res.current_skills == ["Python"]


@pytest.mark.asyncio
async def test_handle_uses_request_current_level_over_profile_lookup(monkeypatch, mock_db):
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)
    monkeypatch.setattr(career_service, "run_query", lambda cypher, params=None: _empty_rows())

    req = CareerRequest(
        user_id=uuid.uuid4(), target_role="Backend Developer",
        current_skills=["Python"], current_level="Senior",
    )
    res = await career_service.handle(req, mock_db)

    assert res.current_level == "Senior"
    mock_db.execute.assert_not_called()


@pytest.mark.asyncio
async def test_handle_auto_load_defaults_target_role_to_profile_job_role_and_target_level_to_current_level(
    monkeypatch, mock_db,
):
    # Auto-load: Java GetCareerRoadmapUseCase chỉ forward user_id, không có target_role/target_level.
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)
    monkeypatch.setattr(career_service, "run_query", lambda cypher, params=None: _empty_rows())
    mock_db.execute.return_value.mappings.return_value.first.return_value = {
        "preferences_json": {"interested_techs": ["Python"]},
        "current_level": "Middle",
        "job_role": "Backend Engineer",
    }

    req = CareerRequest(user_id=uuid.uuid4())
    res = await career_service.handle(req, mock_db)

    assert res.target_role == "Backend Engineer"
    assert res.target_level == "Middle"
    assert res.current_level == "Middle"


@pytest.mark.asyncio
async def test_handle_auto_load_falls_back_to_generic_role_when_profile_has_no_job_role(monkeypatch, mock_db):
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)
    monkeypatch.setattr(career_service, "run_query", lambda cypher, params=None: _empty_rows())
    mock_db.execute.return_value.mappings.return_value.first.return_value = {
        "preferences_json": {"interested_techs": []},
        "current_level": None,
        "job_role": None,
    }

    req = CareerRequest(user_id=uuid.uuid4())
    res = await career_service.handle(req, mock_db)

    assert res.target_role == "Software Engineer"
    assert res.target_level is None


@pytest.mark.asyncio
async def test_handle_manual_search_does_not_default_target_level_to_current_level(monkeypatch, mock_db):
    # Manual search (target_role gửi rõ) — không ép target_level = current_level, để tôn trọng
    # việc user cố tình bỏ trống cấp độ mục tiêu khi tìm 1 role cụ thể.
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)
    monkeypatch.setattr(career_service, "run_query", lambda cypher, params=None: _empty_rows())

    req = CareerRequest(
        user_id=None, target_role="DevOps Engineer", current_skills=["Docker"], current_level="Junior",
    )
    res = await career_service.handle(req, mock_db)

    assert res.target_role == "DevOps Engineer"
    assert res.target_level is None


@pytest.mark.asyncio
async def test_handle_adds_level_distance_to_estimated_months_when_both_levels_known(monkeypatch, mock_db):
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)

    async def fake_run_query(cypher, params=None):
        return [{"skill": "Kubernetes"}, {"skill": "Docker"}, {"skill": "Terraform"}]

    monkeypatch.setattr(career_service, "run_query", fake_run_query)

    req = CareerRequest(
        user_id=None, target_role="Backend Developer", current_skills=[],
        current_level="Junior", target_level="Senior",
    )
    res = await career_service.handle(req, mock_db)

    # gap_skills = 3 skills (none in current_skills) -> base 3*2=6; level_distance(Junior->Senior)=2 -> +6
    assert res.estimated_months == 12


@pytest.mark.asyncio
async def test_handle_estimated_months_unchanged_when_target_level_missing(monkeypatch, mock_db):
    monkeypatch.setattr(career_service, "generate", _fake_generate)
    monkeypatch.setattr(career_service, "sql_analytics_search", _fake_sql_analytics_search)

    async def fake_run_query(cypher, params=None):
        return [{"skill": "Kubernetes"}, {"skill": "Docker"}, {"skill": "Terraform"}]

    monkeypatch.setattr(career_service, "run_query", fake_run_query)

    req = CareerRequest(
        user_id=None, target_role="Backend Developer", current_skills=[],
        current_level="Junior",
    )
    res = await career_service.handle(req, mock_db)

    assert res.estimated_months == 6
    assert res.target_level is None


async def _empty_rows():
    return []
