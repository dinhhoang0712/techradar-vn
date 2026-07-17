from __future__ import annotations

import sys
from pathlib import Path

DATA_PLATFORM_ROOT = Path(__file__).resolve().parents[1]

if str(DATA_PLATFORM_ROOT) not in sys.path:
    sys.path.insert(0, str(DATA_PLATFORM_ROOT))
