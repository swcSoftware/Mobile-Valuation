"""
Thin, SEC-compliant HTTP client.

SEC EDGAR fair-access rules (https://www.sec.gov/os/accessing-edgar-data):
  * Declare a User-Agent of the form "Company/Person Name email@domain".
  * Stay under 10 requests/second.
We also cache JSON responses on disk so repeat lookups for the same company are free.
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import time
from pathlib import Path
from typing import Any

import httpx

from ..config import settings


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


class EdgarClient:
    def __init__(self, user_agent: str | None = None) -> None:
        self.user_agent = (user_agent or settings.sec_user_agent).strip()
        self.cache_dir = Path(settings.cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    # -- caching -----------------------------------------------------------
    def _cache_path(self, url: str) -> Path:
        return self.cache_dir / (hashlib.sha1(url.encode()).hexdigest() + ".json")

    def _read_cache(self, url: str) -> Any | None:
        path = self._cache_path(url)
        if not path.exists():
            return None
        if time.time() - path.stat().st_mtime > settings.cache_ttl_seconds:
            return None
        try:
            return json.loads(path.read_text())
        except json.JSONDecodeError:
            return None

    def _write_cache(self, url: str, payload: Any) -> None:
        self._cache_path(url).write_text(json.dumps(payload))

    # -- fetching ----------------------------------------------------------
    async def get_json(self, url: str, *, use_cache: bool = True) -> Any:
        if use_cache:
            cached = self._read_cache(url)
            if cached is not None:
                return cached

        await _limiter.wait()
        headers = {
            "User-Agent": self.user_agent,
            "Accept-Encoding": "gzip, deflate",
            "Accept": "application/json",
        }
        async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
            resp = await client.get(url, headers=headers)
        if resp.status_code == 404:
            raise EdgarNotFound(url)
        resp.raise_for_status()
        payload = resp.json()
        if use_cache:
            self._write_cache(url, payload)
        return payload


class EdgarError(Exception):
    pass


class EdgarNotFound(EdgarError):
    pass
