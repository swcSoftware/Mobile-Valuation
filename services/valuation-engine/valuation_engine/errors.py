"""
Error taxonomy surfaced to clients.

Every failure the engine can name maps to a stable `code` so the apps can show a specific,
honest message instead of a stack trace. Wire format (all non-2xx responses):

    {"error": {"code": "unknown_ticker", "message": "…", "detail": {...}}, "detail": "…"}

`detail` (string) is kept for backward compatibility with Sprint-0 clients.
"""
from __future__ import annotations

from typing import Any


class EngineError(Exception):
    code = "engine_error"
    status = 500

    def __init__(self, message: str, detail: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.detail = detail or {}

    def to_dict(self) -> dict[str, Any]:
        return {"error": {"code": self.code, "message": self.message, "detail": self.detail}, "detail": self.message}


class UnknownTicker(EngineError):
    code = "unknown_ticker"
    status = 404


class NoAnnualData(EngineError):
    """Company exists on EDGAR but has no 10-K income statement facts (e.g. 20-F filer, fund, SPAC)."""
    code = "no_annual_data"
    status = 422


class UpstreamUnavailable(EngineError):
    """SEC / price / rate provider unreachable or erroring."""
    code = "upstream_unavailable"
    status = 503


class RateLimited(EngineError):
    code = "rate_limited"
    status = 429


class InvalidIdentity(EngineError):
    """X-SEC-User-Agent missing or not of the form 'Name email'."""
    code = "invalid_identity"
    status = 400
