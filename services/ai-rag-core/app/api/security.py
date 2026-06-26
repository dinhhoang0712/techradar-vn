"""
Shared-secret guard for internal endpoints (chat + /internal/ai/*).

The Spring gateway sends the header `X-Internal-Auth: <internal_api_token>`. When
`internal_api_token` is empty (the default) the check is skipped so local dev/tests keep working;
set it in the environment to lock these endpoints down to the gateway only.
"""
import secrets

from fastapi import Header, HTTPException

from app.config import get_settings


async def require_internal_auth(x_internal_auth: str | None = Header(default=None, alias="X-Internal-Auth")):
    expected = get_settings().internal_api_token
    if not expected:
        return  # auth disabled (no token configured)
    if not x_internal_auth or not secrets.compare_digest(x_internal_auth, expected):
        raise HTTPException(status_code=401, detail="Invalid or missing internal auth token")
