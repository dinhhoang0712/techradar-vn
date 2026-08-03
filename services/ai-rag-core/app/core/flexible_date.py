"""Parses article/job date strings pulled from Neo4j, tolerating the mixed formats produced by
different crawler sources — Python counterpart of
apps/backend/.../features/radar/domain/FlexibleDateParser.java.

`Article.published_date` is stored as a plain string (not a native Neo4j Date), so comparing it
against a Cypher `date($start)` value in a WHERE clause always evaluates to NULL (String vs Date)
and gets silently excluded — every date-range filter on this property must therefore fetch the
raw string and filter in application code using `parse_date` below, never in Cypher.

Handled formats:
  "2026-06-30"  -> ISO, unambiguous
  "29/06/2026"  -> dd/MM/yyyy (day > 12 marks the day position)
  "06/30/2026"  -> MM/dd/yyyy (day > 12 marks the day position)
  "05/06/2026"  -> ambiguous (both groups <= 12) -> assumed dd/MM/yyyy, the convention used by
                   the Vietnamese job sites this pipeline crawls (ITviec, TopCV)
"""

import re
from datetime import date

_ISO_DATE = re.compile(r"^(\d{4})-(\d{2})-(\d{2})")
_SLASH_DATE = re.compile(r"^(\d{2})/(\d{2})/(\d{4})")


def parse_date(raw: str | None) -> date | None:
    if not raw:
        return None

    iso = _ISO_DATE.match(raw)
    if iso:
        return _safe_date(int(iso.group(1)), int(iso.group(2)), int(iso.group(3)))

    slash = _SLASH_DATE.match(raw)
    if slash:
        first, second, year = int(slash.group(1)), int(slash.group(2)), int(slash.group(3))
        if first > 12 and second <= 12:
            return _safe_date(year, second, first)  # dd/MM/yyyy
        if second > 12 and first <= 12:
            return _safe_date(year, first, second)  # MM/dd/yyyy
        if first <= 12 and second <= 12:
            return _safe_date(year, second, first)  # ambiguous -> dd/MM/yyyy
        return None  # both > 12: not a valid date either way

    return None


def _safe_date(year: int, month: int, day: int) -> date | None:
    try:
        return date(year, month, day)
    except ValueError:
        return None
