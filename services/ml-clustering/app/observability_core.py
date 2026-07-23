"""
Generic core for structured (JSON) logging + request tracing.

Every FastAPI service in this repo that echoes the gateway's ``X-Request-Id`` header follows the
same shape (see the docstring of the service-level ``app/observability.py`` that wraps this
module). This file deliberately has NO knowledge of any specific service — everything here is
parameterized by ``service_name`` / ``request_id_ctx`` / ``header`` — so it can be copied
byte-for-byte into another service (or lifted into a real shared package once one exists) with
zero edits; only the thin per-service wrapper around it needs to change.

NOTE: there is currently no shared Python package wired across services (each service ships as an
independent Docker image built from its own directory as build context), so this file is, for now,
still physically duplicated per service rather than imported from one place. Until that packaging
work happens (tracked as follow-up), a fix made here must also be copied into the sibling copy/ies.
"""

from __future__ import annotations

import logging
import os
import time
import uuid
from contextvars import ContextVar

from pythonjsonlogger import jsonlogger


def new_request_id_ctx() -> ContextVar[str]:
    """A ContextVar holding the current request's id for the duration of the request (async-safe)."""
    return ContextVar("request_id", default="-")


class RequestIdFilter(logging.Filter):
    """Stamp every record flowing through the JSON handler with the active request id."""

    def __init__(self, request_id_ctx: ContextVar[str]) -> None:
        super().__init__()
        self._request_id_ctx = request_id_ctx

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = self._request_id_ctx.get()
        return True


def configure_json_logging(
    service_name: str,
    request_id_ctx: ContextVar[str],
    level: int | None = None,
) -> None:
    """Route all logging through a single JSON StreamHandler (stdout)."""
    if level is None:
        level = getattr(logging, os.getenv("LOG_LEVEL", "INFO").upper(), logging.INFO)

    handler = logging.StreamHandler()
    formatter = jsonlogger.JsonFormatter(
        "%(asctime)s %(levelname)s %(name)s %(request_id)s %(message)s",
        rename_fields={"asctime": "timestamp", "levelname": "level", "name": "logger"},
        static_fields={"service": service_name},
    )
    handler.setFormatter(formatter)
    handler.addFilter(RequestIdFilter(request_id_ctx))

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level)

    # Make uvicorn use our handler/format instead of its own.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        lg = logging.getLogger(name)
        lg.handlers.clear()
        lg.propagate = True


def make_request_context_middleware(
    *,
    request_id_ctx: ContextVar[str],
    access_logger: logging.Logger,
    header: str,
) -> type:
    """Build a ``RequestContextMiddleware`` class bound to this service's context/logger/header.

    Returns a *class* (not an instance), constructible as ``cls(app)`` — the shape Starlette's
    ``app.add_middleware(cls)`` expects — with ``request_id_ctx`` / ``access_logger`` / ``header``
    already closed over, since ``add_middleware`` gives callers no way to pass extra constructor
    args of their own here.
    """
    header_bytes = header.lower().encode()

    class RequestContextMiddleware:
        """Pure-ASGI middleware: bind the trace id, echo it on the response, log one access line.

        Implemented at the ASGI layer (not ``BaseHTTPMiddleware``) so the ``request_id`` ContextVar
        is set in the SAME task that runs the route handler — otherwise it would not be visible to
        logs emitted inside the endpoint.
        """

        def __init__(self, app):
            self.app = app

        async def __call__(self, scope, receive, send):
            if scope["type"] != "http":
                await self.app(scope, receive, send)
                return

            incoming = dict(scope.get("headers") or {}).get(header_bytes)
            rid = incoming.decode() if incoming else uuid.uuid4().hex[:16]
            token = request_id_ctx.set(rid)
            start = time.perf_counter()
            status_code = 500

            async def send_wrapper(message):
                nonlocal status_code
                if message["type"] == "http.response.start":
                    status_code = message["status"]
                    headers = message.setdefault("headers", [])
                    headers.append((header_bytes, rid.encode()))
                await send(message)

            method = scope.get("method", "-")
            path = scope.get("path", "-")
            try:
                await self.app(scope, receive, send_wrapper)
            except Exception:
                elapsed_ms = (time.perf_counter() - start) * 1000
                access_logger.exception("%s %s -> 500 (%.1f ms)", method, path, elapsed_ms)
                raise
            finally:
                request_id_ctx.reset(token)

            elapsed_ms = (time.perf_counter() - start) * 1000
            level = logging.ERROR if status_code >= 500 else (logging.WARNING if status_code >= 400 else logging.INFO)
            access_logger.log(level, "%s %s -> %d (%.1f ms)", method, path, status_code, elapsed_ms)

    return RequestContextMiddleware
