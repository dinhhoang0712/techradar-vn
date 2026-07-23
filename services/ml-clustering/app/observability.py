"""
Structured (JSON) logging + request tracing for ml-clustering.

A single correlation id (header ``X-Request-Id``) is propagated by the Java gateway and echoed
back here, so one request can be followed across the gateway and this service in aggregated logs.
Every log record carries the current ``request_id`` (``-`` when outside a request).

Usage (in app/main.py, BEFORE creating the FastAPI app):

    from app.observability import configure_logging, RequestContextMiddleware
    configure_logging()
    ...
    app.add_middleware(RequestContextMiddleware)

The actual JSON formatter / request-id ContextVar / ASGI middleware logic lives in
``app/observability_core.py`` and has no ml-clustering-specific knowledge — this module is just
that core wired up with THIS service's name/header. See that module's docstring for why it is
still, for now, a physical copy of services/ai-rag-core/app/observability.py's equivalent.
"""

from __future__ import annotations

import logging

from app.observability_core import (
    configure_json_logging,
    make_request_context_middleware,
    new_request_id_ctx,
)

SERVICE_NAME = "ml-clustering"
REQUEST_ID_HEADER = "X-Request-Id"

# Holds the current request's id for the duration of the request (async-safe).
request_id_ctx = new_request_id_ctx()

# Dedicated access logger used by the middleware.
access_logger = logging.getLogger(f"{SERVICE_NAME}.access")


def configure_logging(level: int | None = None) -> None:
    """Route all logging through a single JSON StreamHandler (stdout)."""
    configure_json_logging(SERVICE_NAME, request_id_ctx, level)


# Bound to this service's context/logger/header; usable directly as ``app.add_middleware(...)``.
RequestContextMiddleware = make_request_context_middleware(
    request_id_ctx=request_id_ctx,
    access_logger=access_logger,
    header=REQUEST_ID_HEADER,
)
