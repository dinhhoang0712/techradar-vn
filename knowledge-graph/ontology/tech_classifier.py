"""
Classify a technology name into (category, subcategory) using taxonomy.py.

The classifier builds a reverse lookup index at module load time so
repeated calls are O(1) for exact matches and O(keywords) for fuzzy.

Usage:
    from ontology import classify_tech
    category, subcategory = classify_tech("React")   # → ("Frontend", "Framework")
    category, subcategory = classify_tech("k8s")     # → ("DevOps", "Orchestration")
                                                     #   (after alias resolution)
"""
from .taxonomy import TAXONOMY, DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY

# ---------------------------------------------------------------------------
# Build reverse index: lowercase keyword → (category, subcategory)
# Built once at import time.
# ---------------------------------------------------------------------------
_INDEX: dict[str, tuple[str, str]] = {}

for _cat, _subcats in TAXONOMY.items():
    for _sub, _keywords in _subcats.items():
        for _kw in _keywords:
            _INDEX[_kw.lower()] = (_cat, _sub)

# Sorted longest-first so substring matching prefers specific keys
_SORTED_KEYWORDS: list[str] = sorted(_INDEX.keys(), key=len, reverse=True)


def classify_tech(name: str) -> tuple[str, str]:
    """
    Return (category, subcategory) for the given technology name.

    Steps:
      1. Exact match (case-insensitive).
      2. Substring match: name contains a keyword, or keyword contains name.
         Longest keyword wins to avoid false positives.
      3. Fallback: (DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY).

    Args:
        name: Technology name, already alias-resolved (from tech_resolver.py).

    Returns:
        Tuple of (category, subcategory) strings.
    """
    if not name:
        return DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY

    lower = name.strip().lower()

    # 1. Exact match
    if lower in _INDEX:
        return _INDEX[lower]

    # 2. Substring match (longest keyword first to minimize false positives)
    for kw in _SORTED_KEYWORDS:
        if kw in lower or lower in kw:
            return _INDEX[kw]

    return DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY


def get_all_categories() -> list[str]:
    """Return sorted list of all top-level categories."""
    return sorted(TAXONOMY.keys())


def get_subcategories(category: str) -> list[str]:
    """Return subcategory names for a given top-level category."""
    return list(TAXONOMY.get(category, {}).keys())
