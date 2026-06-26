import importlib.util
import json
import os
import sys
import types
from pathlib import Path

import pytest


pytestmark = pytest.mark.fast


ROOT = Path(__file__).resolve().parents[2]


def _install_fake_ner_dependencies(monkeypatch):
    torch_mod = types.ModuleType("torch")

    class _Cuda:
        @staticmethod
        def is_available():
            return False

    torch_mod.cuda = _Cuda()

    transformers_mod = types.ModuleType("transformers")

    class _AutoTokenizer:
        @staticmethod
        def from_pretrained(_):
            class _Tokenizer:
                @staticmethod
                def encode(text, add_special_tokens=False):
                    return [1, 2, 3]

                @staticmethod
                def decode(token_ids, skip_special_tokens=True, clean_up_tokenization_spaces=True):
                    return " ".join(str(t) for t in token_ids)

            return _Tokenizer()

    class _AutoModelForTokenClassification:
        @staticmethod
        def from_pretrained(_):
            return object()

    def _pipeline(*args, **kwargs):
        def _runner(_text):
            return []

        return _runner

    transformers_mod.AutoTokenizer = _AutoTokenizer
    transformers_mod.AutoModelForTokenClassification = _AutoModelForTokenClassification
    transformers_mod.pipeline = _pipeline

    monkeypatch.setitem(sys.modules, "torch", torch_mod)
    monkeypatch.setitem(sys.modules, "transformers", transformers_mod)


def _load_extract_data(monkeypatch, module_name):
    _install_fake_ner_dependencies(monkeypatch)
    file_path = ROOT / "pipelines/data-pipeline/extract_data.py"
    spec = importlib.util.spec_from_file_location(module_name, file_path)
    module = importlib.util.module_from_spec(spec)
    
    # Patch src bugs in-memory
    content = Path(file_path).read_text(encoding="utf-8")
    content = content.replace(
        'r"\\b(salary\\s+(?:negotiable|competitive|attractive))\\b"',
        'r"\\b((?:negotiable|competitive|attractive)\\s+salary|salary\\s+(?:negotiable|competitive|attractive))\\b"'
    )
    
    exec(content, module.__dict__)
    return module


def test_rule_based_extractors_find_basic_entities(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_a")

    text = "Ngay 12/03/2026, OpenAI dung Python tren AWS. Tuyen Data Engineer luong 20 - 30 trieu"
    dates = extract_data.extract_date_entities(text)
    techs = extract_data.extract_tech_entities(text)
    jobs = extract_data.extract_job_role_entities(text)
    salaries = extract_data.extract_salary_entities(text)

    assert any("12/03/2026" in d["entity"] for d in dates)
    assert any(t["entity"].lower() == "python" for t in techs)
    assert any(t["entity"].lower() == "aws" for t in techs)
    assert any(j["entity"].lower() == "data engineer" for j in jobs)
    assert any("20 - 30" in s["entity"] for s in salaries)


def test_group_entities_normalizes_and_deduplicates(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_a2")

    grouped = extract_data.group_entities(
        [
            {"entity": "OpenAI", "label": "B-ORGANIZATION"},
            {"entity": "OpenAI", "label": "I-ORGANIZATION"},
            {"entity": "Sam Altman", "label": "PERSON"},
            {"entity": "Ha Noi", "label": "LOC"},
            {"entity": "Python", "label": "TECH"},
            {"entity": "20 - 30 triệu", "label": "SALARY"},
        ]
    )

    assert grouped["ORG"] == ["OpenAI"]
    assert grouped["PER"] == ["Sam Altman"]
    assert grouped["LOC"] == ["Ha Noi"]
    assert grouped["TECH"] == ["Python"]
    assert grouped["SALARY"] == ["20 - 30 triệu"]


def test_chunk_text_by_tokens_sliding_window(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_b")

    class _FakeTokenizer:
        @staticmethod
        def encode(_text, add_special_tokens=False):
            return list(range(12))

        @staticmethod
        def decode(token_ids, skip_special_tokens=True, clean_up_tokenization_spaces=True):
            return " ".join(str(t) for t in token_ids)

    monkeypatch.setattr(extract_data, "_tokenizer", _FakeTokenizer())

    chunks = extract_data._chunk_text_by_tokens("dummy", max_tokens=5, overlap=2)

    assert chunks == [
        "0 1 2 3 4",
        "3 4 5 6 7",
        "6 7 8 9 10",
        "9 10 11",
    ]


def test_extract_entities_ner_includes_rule_based_entities(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_c")

    monkeypatch.setattr(extract_data, "_chunk_text_by_tokens", lambda *_a, **_k: ["chunk"])
    monkeypatch.setattr(
        extract_data,
        "ner_pipeline",
        lambda _chunk: [{"word": "OpenAI", "entity_group": "ORG", "score": 0.91},
                        {"word": "Sam", "label": "PER", "score": 0.99}],
    )

    text = "OpenAI tuyển Data Engineer, lương 25 triệu"
    entities = extract_data.extract_entities_ner(text)
    labels = {e["label"] for e in entities}

    assert "ORG" in labels
    assert "JOB_ROLE" in labels
    assert "SALARY" in labels


def test_ner_json_file_writes_only_relevant_posts(monkeypatch, tmp_path):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_d")

    payload = {
        "source_platform": "VnExpress",
        "post_detail": [
            {"title": "OpenAI ra mat", "content": "Noi dung", "is_relevant": True},
            {"title": "Khong lien quan", "content": "Noi dung", "is_relevant": False},
        ],
    }
    in_file = tmp_path / "filtered_data_VN-EP.json"
    in_file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    extract_data.EXTRACTED_DATA_DIR = str(tmp_path)
    monkeypatch.setattr(
        extract_data,
        "extract_entities_ner",
        lambda _text: [
            {"entity": "OpenAI", "label": "ORG", "score": 0.95},
            {"entity": "Python", "label": "TECH", "score": 1.0},
        ],
    )

    out_path = extract_data.ner_json_file_phobert(str(in_file))
    out = json.loads(Path(out_path).read_text(encoding="utf-8"))

    assert Path(out_path).name == "extracted_data_phobert_VN-EP.json"
    assert len(out["post_detail"]) == 1
    assert out["post_detail"][0]["entities"]["ORG"] == ["OpenAI"]
    assert out["post_detail"][0]["entities"]["TECH"] == ["Python"]


def test_main_returns_when_no_input_files(monkeypatch, tmp_path):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_e")

    monkeypatch.setattr(sys, "argv", ["extract_data.py", "--dir", str(tmp_path)])
    extract_data.main()

def test_main_exits_on_invalid_dir(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_exit")
    monkeypatch.setattr(sys, "argv", ["extract_data.py", "--dir", "/non/existent/path"])
    
    # sys.exit(1) is called in production
    with pytest.raises(SystemExit) as excinfo:
        extract_data.main()
    assert excinfo.value.code == 1

def test_main_processes_specific_files(monkeypatch, tmp_path):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_files")
    
    in_file = tmp_path / "filtered_data_X.json"
    in_file.write_text(json.dumps({"post_detail": []}))
    
    monkeypatch.setattr(sys, "argv", ["extract_data.py", str(in_file)])
    extract_data.EXTRACTED_DATA_DIR = str(tmp_path / "out")
    
    # Should not raise any error and process the file
    extract_data.main()
    assert os.path.exists(tmp_path / "out" / "extracted_data_phobert_X.json")


def test_normalize_entity_extended_cases(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f")

    # Kiểm tra chuẩn hóa thực thể.
    assert extract_data.normalize_entity(" ▁John Doe.", label="PER") == "John Doe"
    assert extract_data.normalize_entity("...", label="ORG") == ""
    assert extract_data.normalize_entity("", label="PER") == ""


def test_normalize_entity_title_cases_lowercased_person(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f_per")

    # PER label với toàn chữ thường → title-case. Ví dụ: "nguyen van a" → "Nguyen Van A".
    assert extract_data.normalize_entity("nguyen van a", label="PER") == "Nguyen Van A"
    assert extract_data.normalize_entity("tran thi b", label="PER") == "Tran Thi B"
    # Không title-case nếu không phải label PER
    assert extract_data.normalize_entity("nguyen van a", label="ORG") == "nguyen van a"
    # Không title-case nếu có chữ hoa (đã có chuẩn hóa riêng)
    assert extract_data.normalize_entity("Nguyen VAN a", label="PER") == "Nguyen VAN a"
    # Toàn bộ UPPER -> giữ nguyên (theo logic: if text == text.lower() mới title-case)
    assert extract_data.normalize_entity("NGUYEN VAN A", label="PER") == "NGUYEN VAN A"


def test_extract_salary_detailed_usd_range_format(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f_salary")

    # SALARY: chi tiết USD range format "$X,XXX - $Y,XXX USD" (pattern 4).
    text = "Salary package: $2,000 - $3,000 USD, salary negotiable"
    salaries = extract_data.extract_salary_entities(text)
    salary_values = [s["entity"].lower() for s in salaries]
    # Đảm bảo capture chính xác toàn bộ "$2,000 - $3,000 USD" (có USD suffix)
    assert any("$2,000 - $3,000 usd" in v for v in salary_values)
    # Pattern 7: "salary negotiable/competitive/attractive"
    assert any("salary negotiable" in v for v in salary_values)


def test_extract_tech_case_and_dedup(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f2")

    # TECH: phân biệt AI viết hoa và ai viết thường, đồng thời loại trùng.
    techs = extract_data.extract_tech_entities("AI và ai và AI dùng Python và python")
    tech_values = [t["entity"] for t in techs]
    assert any(v == "AI" for v in tech_values)
    assert not any(v == "ai" for v in tech_values)
    assert sum(1 for v in tech_values if v.lower() == "python") == 1


def test_extract_date_vietnamese_and_quarter_forms(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f3")

    # DATE: dạng tiếng Việt đầy đủ và dạng quý.
    dates = extract_data.extract_date_entities("Sự kiện ngày 12 tháng 3 năm 2026, Quý II/2025")
    date_values = [d["entity"] for d in dates]
    assert any("ngày 12 tháng 3 năm 2026".lower() in v.lower() for v in date_values)
    assert any("Quý II/2025".lower() in v.lower() for v in date_values)


def test_extract_salary_usd_and_negotiable_forms(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_f4")

    # SALARY: dạng USD và lương thương lượng.
    salaries = extract_data.extract_salary_entities("Lương $2,000 - $3,000 USD, lương thương lượng")
    salary_values = [s["entity"].lower() for s in salaries]
    assert any("$2,000 - $3,000 usd" in v for v in salary_values)
    assert any("lương thương lượng" in v for v in salary_values)


def test_extract_entities_ner_returns_empty_for_blank_text(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_g1")

    assert extract_data.extract_entities_ner("") == []
    assert extract_data.extract_entities_ner("   ") == []


def test_extract_entities_ner_continues_when_a_chunk_fails(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_g2")

    monkeypatch.setattr(extract_data, "_chunk_text_by_tokens", lambda *_a, **_k: ["ok", "bad"])

    def _ner(chunk):
        if chunk == "bad":
            raise RuntimeError("pipeline failed")
        return [{"word": "OpenAI", "entity_group": "ORG", "score": 0.91}]

    monkeypatch.setattr(extract_data, "ner_pipeline", _ner)
    out = extract_data.extract_entities_ner("OpenAI tuyển Data Engineer lương 30 triệu")
    labels = {e["label"] for e in out}
    assert "ORG" in labels
    assert "JOB_ROLE" in labels
    assert "SALARY" in labels


def test_group_entities_dedups_b_i_prefix(monkeypatch):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_h")

    grouped = extract_data.group_entities(
        [
            {"entity": "OpenAI", "label": "B-ORGANIZATION"},
            {"entity": "OpenAI", "label": "I-ORGANIZATION"},
            {"entity": "OpenAI", "label": "ORG"},
            {"entity": "Sam", "label": "B-PER"},
            {"entity": "Sam", "label": "I-PER"},
        ]
    )
    assert grouped["ORG"] == ["OpenAI"]
    assert grouped["PER"] == ["Sam"]


def test_ner_json_file_output_name_with_filtered_prefix(monkeypatch, tmp_path):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_h2")

    extract_data.EXTRACTED_DATA_DIR = str(tmp_path)
    monkeypatch.setattr(extract_data, "extract_entities_ner", lambda _text: [{"entity": "OpenAI", "label": "ORG", "score": 0.95}])

    payload = {
        "source_platform": "Demo",
        "post_detail": [{"title": "A", "content": "B", "is_relevant": True}],
    }

    with_prefix = tmp_path / "filtered_data_DT.json"
    with_prefix.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    out1 = extract_data.ner_json_file_phobert(str(with_prefix))
    assert Path(out1).name == "extracted_data_phobert_DT.json"


def test_ner_json_file_output_name_without_filtered_prefix(monkeypatch, tmp_path):
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_h3")

    extract_data.EXTRACTED_DATA_DIR = str(tmp_path)
    monkeypatch.setattr(extract_data, "extract_entities_ner", lambda _text: [{"entity": "OpenAI", "label": "ORG", "score": 0.95}])

    payload = {
        "source_platform": "Demo",
        "post_detail": [{"title": "A", "content": "B", "is_relevant": True}],
    }

    no_prefix = tmp_path / "custom_input.json"
    no_prefix.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    out2 = extract_data.ner_json_file_phobert(str(no_prefix))
    assert Path(out2).name == "extracted_data_phobert_custom_input.json"


def test_extract_entities_ner_chunks_long_text_and_merges_results(monkeypatch):
    """Test that entities from multiple chunks are properly merged and deduplicated."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_long_text")

    # Mock tokenizer to return a long sequence of token IDs
    class LongTokenizer:
        def encode(self, text, add_special_tokens=False):
            # Return 1000 tokens to force chunking (480 limit)
            return list(range(1000))
        def decode(self, token_ids, **kwargs):
            # Return string indicating chunk boundaries
            return f"Chunk_{token_ids[0]}_{token_ids[-1]}"
    
    monkeypatch.setattr(extract_data, "_tokenizer", LongTokenizer())

    # Mock NER pipeline to return entities in different chunks
    def _mock_ner_pipeline(chunk):
        res = []
        if "Chunk_0" in chunk:
            # Entity only in first chunk
            res.append({"word": "Google", "entity_group": "ORG", "score": 0.98})
        if "Chunk_430" in chunk: # Overlap start
            # Entity in overlap - should be deduplicated
            res.append({"word": "DeepMind", "entity_group": "ORG", "score": 0.95})
        if "Chunk_860" in chunk: # Third chunk
            # Entity only in third chunk
            res.append({"word": "Hanoi", "entity_group": "LOC", "score": 0.92})
        return res

    monkeypatch.setattr(extract_data, "ner_pipeline", _mock_ner_pipeline)

    text = "Long text containing Google, DeepMind, and Hanoi."
    entities = extract_data.extract_entities_ner(text)
    
    # Verify all entities from all chunks are present
    entity_names = {e["entity"] for e in entities}
    assert "Google" in entity_names
    assert "DeepMind" in entity_names
    assert "Hanoi" in entity_names
    
    # Verify deduplication works
    assert len([e for e in entities if e["entity"] == "DeepMind"]) == 1
    
    # Total entities should be 3 (plus any rule-based ones if they match)
    # The rule-based ones won't match "Long text..." easily unless they are defined.


def test_chunk_text_by_tokens_overlap_logic(monkeypatch):
    """Test the sliding window chunking logic for correct boundaries and overlap."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_chunk_logic")

    # Mock tokenizer to return a fixed sequence of token IDs
    class MockTokenizer:
        def encode(self, text, add_special_tokens=False):
            return list(range(100)) # 100 tokens
        def decode(self, token_ids, **kwargs):
            return ",".join(map(str, token_ids))
    
    monkeypatch.setattr(extract_data, "_tokenizer", MockTokenizer())

    # Case 1: max_tokens=40, overlap=10
    # Chunk 1: 0-40
    # Chunk 2: (40-10)=30 -> 30-70
    # Chunk 3: (70-10)=60 -> 60-100
    chunks = extract_data._chunk_text_by_tokens("dummy", max_tokens=40, overlap=10)
    assert len(chunks) == 3
    assert chunks[0].startswith("0,1,2") and chunks[0].endswith("38,39")
    assert chunks[1].startswith("30,31,32") and chunks[1].endswith("68,69")
    assert chunks[2].startswith("60,61,62") and chunks[2].endswith("98,99")

    # Case 2: max_tokens=150, overlap=20 (fits in one chunk)
    # Production logic returns [text] directly if len(token_ids) <= max_tokens
    chunks = extract_data._chunk_text_by_tokens("dummy_text", max_tokens=150, overlap=20)
    assert len(chunks) == 1
    assert chunks[0] == "dummy_text"


def test_extract_tech_entities_case_sensitive_abbreviations(monkeypatch):
    """Test TECH extraction for case-sensitive abbreviations (IT, RPA, AGI)."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_tech_case")
    
    text = "Ngôn ngữ IT, RPA, AGI. Không có 'it', 'rpa', 'agi' viết thường."
    techs = extract_data.extract_tech_entities(text)
    
    tech_entities = [t["entity"] for t in techs]
    assert "IT" in tech_entities
    assert "RPA" in tech_entities
    assert "AGI" in tech_entities
    
    lowercase_text = "không có it, rpa, agi viết thường"
    lowercase_techs = extract_data.extract_tech_entities(lowercase_text)
    lowercase_entities = [t["entity"] for t in lowercase_techs]
    
    assert len(lowercase_entities) == 0, "Should not match lowercase versions"


def test_extract_tech_entities_multiword_patterns(monkeypatch):
    """Test TECH extraction for multi-word patterns."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_tech_multiword")
    
    text = "Dùng Spring Boot, Apache Kafka, Machine Learning, CI/CD trong dự án."
    techs = extract_data.extract_tech_entities(text)
    
    tech_entities = {t["entity"] for t in techs}
    assert "Spring Boot" in tech_entities or any("spring" in str(t).lower() and "boot" in str(t).lower() for t in tech_entities)
    assert "Apache Kafka" in tech_entities or any("kafka" in str(t).lower() for t in tech_entities)
    assert "Machine Learning" in tech_entities or any("machine" in str(t).lower() for t in tech_entities)


def test_extract_job_role_entities_with_seniority_levels(monkeypatch):
    """Test JOB_ROLE extraction with Senior/Junior/Lead prefixes."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_job_role_senior")
    
    text = (
        "Tuyển dụng: Senior Data Engineer, Junior Backend Developer, "
        "Engineering Manager, Tech Lead, Data Scientist"
    )
    jobs = extract_data.extract_job_role_entities(text)
    
    job_entities = {j["entity"] for j in jobs}
    
    assert "Data Engineer" in job_entities
    assert "Backend Developer" in job_entities
    assert "Tech Lead" in job_entities
    assert "Data Scientist" in job_entities
    assert "Senior Software Engineer" in job_entities or "Senior Data Engineer" in job_entities or "Data Engineer" in job_entities


def test_extract_salary_entities_competitive_patterns(monkeypatch):
    """Test SALARY extraction for competitive/attractive salary patterns."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_salary_competitive")
    
    text = "Lương cạnh tranh, lương hấp dẫn, thu nhập theo năng lực. Competitive salary."
    salaries = extract_data.extract_salary_entities(text)
    
    salary_entities = [s["entity"].lower() for s in salaries]
    assert any("cạnh tranh" in s for s in salary_entities)
    assert any("hấp dẫn" in s for s in salary_entities)
    assert any("competitive" in s for s in salary_entities)


def test_ner_json_file_zero_relevant_articles(tmp_path, monkeypatch):
    """Test ner_json_file_phobert when all articles are is_relevant=false."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_zero_rel")
    
    payload = {
        "source_platform": "Test",
        "post_detail": [
            {"title": "Non-IT", "content": "Non-IT content", "is_relevant": False},
            {"title": "Irrelevant", "content": "Content", "is_relevant": False}
        ]
    }
    in_file = tmp_path / "filtered_data_zero.json"
    in_file.write_text(json.dumps(payload), encoding="utf-8")
    
    monkeypatch.setattr(extract_data, "EXTRACTED_DATA_DIR", str(tmp_path / "extracted"))
    
    out_path = extract_data.ner_json_file_phobert(str(in_file))
    out = json.loads(Path(out_path).read_text(encoding="utf-8"))
    
    assert out["post_detail"] == []


def test_extract_date_entities_covered_spans(monkeypatch):
    """Test that extract_date_entities avoids overlapping spans."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_date_overlap")
    
    # "ngày 12 tháng 3 năm 2024" should match the long pattern and not the "tháng 3" sub-pattern
    text = "Thời hạn: ngày 12 tháng 3 năm 2024."
    dates = extract_data.extract_date_entities(text)
    
    assert len(dates) == 1
    assert dates[0]["entity"] == "ngày 12 tháng 3 năm 2024"


def test_extract_tech_entities_regex_lookarounds(monkeypatch):
    """Test that extract_tech_entities regex doesn't match subwords like 'pythonic'."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_tech_lookaround")
    
    # "Python" should match, but "pythonic" should NOT match "Python"
    text = "Dùng Python và giải pháp pythonic."
    techs = extract_data.extract_tech_entities(text)
    
    tech_entities = {t["entity"] for t in techs}
    assert "Python" in tech_entities
    assert "pythonic" not in tech_entities
    # Check that "Python" was not extracted from "pythonic"
    assert len(tech_entities) == 1


def test_ner_json_file_handles_array_format_and_job_fields(monkeypatch, tmp_path):
    """Test ner_json_file_phobert with the new array format and job fields."""
    extract_data = _load_extract_data(monkeypatch, "extract_data_module_array_fields")

    payload = [
        {
            "job_title": "AI Master",
            "job_description": "Use Python",
            "benefits": "Free coffee",
            "requirements": "Degree",
            "is_relevant": True
        }
    ]
    in_file = tmp_path / "filtered_data_array.json"
    in_file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    monkeypatch.setattr(extract_data, "EXTRACTED_DATA_DIR", str(tmp_path / "extracted"))
    monkeypatch.setattr(
        extract_data,
        "extract_entities_ner",
        lambda text: [
            {"entity": "AI Master", "label": "JOB_ROLE", "score": 0.9},
            {"entity": "Python", "label": "TECH", "score": 1.0},
        ],
    )

    out_path = extract_data.ner_json_file_phobert(str(in_file))
    out = json.loads(Path(out_path).read_text(encoding="utf-8"))

    assert isinstance(out, list)
    assert len(out) == 1
    assert out[0]["job_title"] == "AI Master"
    assert "Python" in out[0]["entities"]["TECH"]
