"""
Normalize company names before ingestion into Neo4j.

Two-step normalization:
  1. Explicit alias lookup (handles known variants like VNG Corp → VNG).
  2. Legal-suffix stripping for remaining names.
"""
import json
import re
from pathlib import Path
from typing import Optional

_ALIASES_PATH = Path(__file__).parent / "aliases.json"

def _load_company_aliases() -> dict[str, str]:
    with open(_ALIASES_PATH, encoding="utf-8") as f:
        data = json.load(f)
    return {k.lower(): v for k, v in data.get("company_aliases", {}).items()}

_COMPANY_ALIASES: dict[str, str] = _load_company_aliases()

# Legal suffixes commonly appended to Vietnamese company names
_SUFFIX_RE = re.compile(
    r"\s*[\-–]?\s*"
    r"(corporation|corp\.?|co\.?\s*ltd\.?|limited|inc\.?|llc\.?|jsc\.?|"
    r"công ty|tập đoàn|group|vietnam|việt nam|viet nam)\s*$",
    re.IGNORECASE,
)

_MULTI_SPACE = re.compile(r"\s+")

_MIN_LEN = 3  # names shorter than this are almost always noise


def resolve_company(name: str) -> Optional[str]:
    """Return canonical company name, or None if too short / empty."""
    if not name or not name.strip():
        return None
    cleaned = _MULTI_SPACE.sub(" ", name.strip())
    if len(cleaned) < _MIN_LEN:
        return None

    # Explicit alias wins
    aliased = _COMPANY_ALIASES.get(cleaned.lower())
    if aliased:
        return aliased

    # Strip legal suffixes iteratively (e.g. "FPT Group Vietnam" → "FPT Group" → "FPT Group")
    stripped = _SUFFIX_RE.sub("", cleaned).strip()
    return stripped if stripped else cleaned


def resolve_company_list(names: list[str]) -> list[str]:
    """Resolve and deduplicate a list of company names."""
    seen: dict[str, bool] = {}
    result: list[str] = []
    for name in names:
        resolved = resolve_company(name)
        if resolved is None:
            continue
        key = resolved.lower()
        if key not in seen:
            seen[key] = True
            result.append(resolved)
    return result
