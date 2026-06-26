"""
Structured (JSON) logging + request tracing for ai-rag-core.

A single correlation id (header ``X-Request-Id``) is propagated by the Java gateway and echoed
back here, so one request can be followed across the gateway and this service in aggregated logs.
Every log record carries the current ``request_id`` (``-`` when outside a request).

Usage (in app/main.py, BEFORE creating the FastAPI app):

    from app.observability import configure_logging, RequestContextMiddleware
    configure_logging()
    ...
    app.add_middleware(RequestContextMiddleware)
"""
from __future__ import annotations

import logging
import os
import time
import uuid
from contextvars import ContextVar

from pythonjsonlogger import jsonlogger

SERVICE_NAME = "ai-rag-core"
REQUEST_ID_HEADER = "X-Request-Id"

# Holds the current request's id for the duration of the request (async-safe).
request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")

# Dedicated access logger used by the middleware.
access_logger = logging.getLogger(f"{SERVICE_NAME}.access")


class _RequestIdFilter(logging.Filter):
    """Stamp every record flowing through the JSON handler with the active request id."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_ctx.get()
        return True


def configure_logging(level: int | None = None) -> None:
    """Route all logging through a single JSON StreamHandler (stdout)."""
    if level is None:
        level = getattr(logging, os.getenv("LOG_LEVEL", "INFO").upper(), logging.INFO)

    handler = logging.StreamHandler()
    formatter = jsonlogger.JsonFormatter(
        "%(asctime)s %(levelname)s %(name)s %(request_id)s %(message)s",
        rename_fields={"asctime": "timestamp", "levelname": "level", "name": "logger"},
        static_fields={"service": SERVICE_NAME},
    )
    handler.setFormatter(formatter)
    handler.addFilter(_RequestIdFilter())

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level)

    # Make uvicorn use our handler/format instead of its own.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        lg = logging.getLogger(name)
        lg.handlers.clear()
        lg.propagate = True


_HEADER_BYTES = REQUEST_ID_HEADER.lower().encode()


class RequestContextMiddleware:
    """Pure-ASGI middleware: bind the trace id, echo it on the response, log one access line.

    Implemented at the ASGI layer (not ``BaseHTTPMiddleware``) so the ``request_id`` ContextVar is
    set in the SAME task that runs the route handler — otherwise it would not be visible to logs
    emitted inside the endpoint.
    """

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        incoming = dict(scope.get("headers") or {}).get(_HEADER_BYTES)
        rid = incoming.decode() if incoming else uuid.uuid4().hex[:16]
        token = request_id_ctx.set(rid)
        start = time.perf_counter()
        status_code = 500

        async def send_wrapper(message):
            nonlocal status_code
            if message["type"] == "http.response.start":
                status_code = message["status"]
                headers = message.setdefault("headers", [])
                headers.append((_HEADER_BYTES, rid.encode()))
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
        level = logging.ERROR if status_code >= 500 else (
            logging.WARNING if status_code >= 400 else logging.INFO
        )
        access_logger.log(level, "%s %s -> %d (%.1f ms)", method, path, status_code, elapsed_ms)
