"""
Thin, SEC-compliant HTTP client.

SEC EDGAR fair-access rules (https://www.sec.gov/os/accessing-edgar-data):
  * Declare a User-Agent of the form "Company/Person Name email@domain".
  * Stay under 10 requests/second.
We also cache JSON responses on disk so repeat lookups for the same company are free.
"""
from __future__ import annotations

import asyncio
import time
from typing import Any

import httpx

from ..cache import SQLiteCache
from ..config import settings
from ..errors import NoAnnualData, UpstreamUnavailable


class SECRateLimiter:
    """Simple token-interval limiter shared across the process."""

    def __init__(self, max_per_second: float) -> None:
        self._interval = 1.0 / max_per_second
        self._last = 0.0
        self._lock = asyncio.Lock()

    async def wait(self) -> None:
        async with self._lock:
            now = time.monotonic()
            delta = now - self._last
            if delta < self._interval:
                await asyncio.sleep(self._interval - delta)
            self._last = time.monotonic()


_limiter = SECRateLimiter(settings.sec_max_requests_per_second)
_cache = SQLiteCache(settings.cache_db, settings.cache_ttl_seconds)


def cache() -> SQLiteCache:
    return _cache


class EdgarClient:
    def __init__(self, user_agent: str | None = None) -> None:
        self.user_agent = (user_agent or settings.sec_user_agent).strip()

    async def get_json(self, url: str, *, use_cache: bool = True) -> Any:
        if use_cache:
            cached = _cache.get(url)
            if cached is not None:
                return cached

        await _limiter.wait()
        headers = {
            "User-Agent": self.user_agent,
            "Accept-Encoding": "gzip, deflate",
            "Accept": "application/json",
        }
        try:
            async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
                resp = await client.get(url, headers=headers)
        except httpx.HTTPError as e:
            raise UpstreamUnavailable(f"SEC EDGAR unreachable: {e.__class__.__name__}", {"url": url}) from e
        if resp.status_code == 404:
            raise NoAnnualData("SEC has no XBRL company facts for this filer.", {"url": url})
        if resp.status_code >= 500 or resp.status_code == 403:
            raise UpstreamUnavailable(f"SEC EDGAR returned {resp.status_code}", {"url": url})
        resp.raise_for_status()
        payload = resp.json()
        if use_cache:
            _cache.set(url, payload)
        return payload

