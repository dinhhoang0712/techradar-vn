"""Kiểm tra việc merge seed alias (dp_tech_alias_map export) vào TECH_ALIAS_MAP."""
import json

from src.features.tech_aliases import (
    TECH_ALIAS_MAP,
    _build_merged_alias_map,
    _load_seed_aliases,
    canonical_tech_name,
    normalize_tech_key,
)


def test_load_seed_aliases_normalizes_keys_with_separators(tmp_path):
    seed_file = tmp_path / "seed.json"
    seed_file.write_text(json.dumps({"aliases": {"express.js": "Express", "react-native": "React Native"}}))

    result = _load_seed_aliases(seed_file)

    assert result == {"express js": "Express", "react native": "React Native"}


def test_load_seed_aliases_missing_file_returns_empty(tmp_path):
    assert _load_seed_aliases(tmp_path / "does_not_exist.json") == {}


def test_load_seed_aliases_invalid_json_returns_empty(tmp_path):
    seed_file = tmp_path / "seed.json"
    seed_file.write_text("{not valid json")

    assert _load_seed_aliases(seed_file) == {}


def test_build_merged_alias_map_seed_overrides_hardcoded_on_collision(tmp_path):
    seed_file = tmp_path / "seed.json"
    seed_file.write_text(json.dumps({"aliases": {"vuejs": "Vue"}}))
    hardcoded = {"vuejs": "Vue.js", "django": "Django"}

    merged = _build_merged_alias_map(hardcoded, seed_file)

    assert merged["vuejs"] == "Vue"  # seed thắng khi trùng key
    assert merged["django"] == "Django"  # entry chỉ có ở hardcoded vẫn giữ nguyên


def test_build_merged_alias_map_falls_back_to_hardcoded_when_seed_missing(tmp_path):
    hardcoded = {"django": "Django"}

    merged = _build_merged_alias_map(hardcoded, tmp_path / "missing.json")

    assert merged == hardcoded


def test_canonical_tech_name_uses_real_seed_file_vue_and_express():
    # Dùng chính SEED_ALIAS_FILE thật (đã export từ dp_tech_alias_map) — đây là 2
    # case mismatch đã phát hiện giữa dp_tech_alias_map và TECH_ALIAS_MAP cũ:
    # dp canonical là "Vue"/"Express", TECH_ALIAS_MAP cũ (trước khi merge) là
    # "Vue.js"/"Express.js". Sau khi merge, seed phải thắng.
    assert canonical_tech_name("VueJS") == "Vue"
    assert canonical_tech_name("vue.js") == "Vue"
    assert canonical_tech_name("ExpressJS") == "Express"


def test_canonical_tech_name_still_resolves_hardcoded_only_entries():
    # "3ds max"/"figma" không có trong dp_tech_alias_map (chỉ ml-clustering quan
    # tâm design tools) — phải vẫn resolve qua TECH_ALIAS_MAP cũ.
    assert canonical_tech_name("Figma") == "Figma"
    assert canonical_tech_name("3ds Max") == "3ds Max"
    assert canonical_tech_name("học máy") == "Machine Learning"


def test_canonical_tech_name_unknown_name_falls_back_to_trimmed_original():
    assert canonical_tech_name("  SomeRandomTech  ") == "SomeRandomTech"


def test_normalize_tech_key_matches_dp_tech_alias_map_separator_variants():
    # dp_tech_alias_map lưu "spring-boot"/"springboot" như 2 hàng riêng; ml-clustering
    # collapse separator nên cả 2 phải cùng resolve về 1 canonical name sau merge.
    assert normalize_tech_key("spring-boot") == normalize_tech_key("spring boot")
    assert canonical_tech_name("spring-boot") == canonical_tech_name("springboot") == "Spring Boot"
