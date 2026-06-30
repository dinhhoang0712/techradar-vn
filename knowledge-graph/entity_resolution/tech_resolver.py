"""
Normalize technology names before ingestion into Neo4j.

Resolves variant spellings and abbreviations to canonical forms using
aliases.json, preventing duplicate Technology nodes in the graph.
"""
import json
import re
from pathlib import Path
from typing import Optional

_ALIASES_PATH = Path(__file__).parent / "aliases.json"

def _load_tech_aliases() -> dict[str, str]:
    with open(_ALIASES_PATH, encoding="utf-8") as f:
        data = json.load(f)
    return {k.lower(): v for k, v in data.get("tech_aliases", {}).items()}

_TECH_ALIASES: dict[str, str] = _load_tech_aliases()

_MULTI_SPACE = re.compile(r"\s+")


def resolve_tech(name: str) -> Optional[str]:
    """Return canonical tech name, or None if name is empty/invalid."""
    if not name or not name.strip():
        return None
    cleaned = _MULTI_SPACE.sub(" ", name.strip())
    return _TECH_ALIASES.get(cleaned.lower(), cleaned)


def resolve_tech_list(names: list[str]) -> list[str]:
    """Resolve and deduplicate a list of tech names.

    Deduplication is case-insensitive so 'Python' and 'python' collapse to one.
    """
    seen: dict[str, bool] = {}
    result: list[str] = []
    for name in names:
        resolved = resolve_tech(name)
        if resolved is None:
            continue
        key = resolved.lower()
        if key not in seen:
            seen[key] = True
            result.append(resolved)
    return result
