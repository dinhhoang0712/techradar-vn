"""
Shared-secret guard for internal endpoints (`PUT /clusters/{id}/label`,
`POST /pipeline/trigger`).

Mirrors `services/ai-rag-core/app/api/security.py`: the Spring gateway sends
`X-Internal-Auth: <INTERNAL_API_TOKEN>`. When `INTERNAL_API_TOKEN` is unset/empty
(the default) the check is skipped so local dev/tests keep working; set it in the
environment to lock these endpoints down to the gateway only.

Uses `secrets.compare_digest` (constant-time) instead of `!=` so response timing
can't be used to brute-force the token byte-by-byte from inside the cluster network.
"""

from __future__ import annotations

import os
import secrets

from fastapi import Header, HTTPException


def require_internal_auth(
    x_internal_auth: str | None = Header(default=None, alias="X-Internal-Auth"),
) -> None:
    expected = os.getenv("INTERNAL_API_TOKEN", "")
    if not expected:
        return  # auth disabled (no token configured)
    if not x_internal_auth or not secrets.compare_digest(x_internal_auth, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")
