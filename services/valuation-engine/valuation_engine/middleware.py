"""Request logging, identity validation and per-identity rate limiting."""
from __future__ import annotations

import logging
import re
import time
from collections import defaultdict, deque

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse

from .config import settings
from .errors import InvalidIdentity, RateLimited

log = logging.getLogger("valuation_engine.access")
IDENTITY_RE = re.compile(r"^\S+(?:\s+\S+)+\s+[^\s@]+@[^\s@]+\.[^\s@]+$")  # "First Last email@domain"
OPEN_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}


def valid_identity(ua: str | None) -> bool:
    return bool(ua) and bool(IDENTITY_RE.match(ua.strip()))


class ClientRateLimiter:
    """Sliding-window limiter keyed by identity (falls back to client IP)."""

    def __init__(self, per_minute: int) -> None:
        self.per_minute = per_minute
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def check(self, key: str) -> tuple[bool, int]:
        if self.per_minute <= 0:
            return True, 0
        now = time.monotonic()
        q = self._hits[key]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= self.per_minute:
            return False, int(60 - (now - q[0])) + 1
        q.append(now)
        return True, 0


limiter = ClientRateLimiter(settings.client_rate_limit_per_minute)


class AccessMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        ua = request.headers.get("x-sec-user-agent")
        identity = ua.strip() if ua else None
        key = identity or (request.client.host if request.client else "anon")
        path = request.url.path

        if path not in OPEN_PATHS:
            if settings.require_identity and not valid_identity(identity):
                err = InvalidIdentity("X-SEC-User-Agent must be 'Full Name email@domain' (SEC fair-access policy).")
                return JSONResponse(err.to_dict(), status_code=err.status)
            ok, retry = limiter.check(key)
            if not ok:
                err = RateLimited(f"Too many requests; retry in {retry}s.", {"retry_after_s": retry})
                return JSONResponse(err.to_dict(), status_code=err.status, headers={"Retry-After": str(retry)})

        response = await call_next(request)
        ms = (time.perf_counter() - start) * 1000
        log.info("%s %s %s %.0fms identity=%s", request.method, path, response.status_code, ms, identity or "-")
        return response
