"""
Interest-rate inputs from FRED (Federal Reserve Bank of St. Louis).

  DAAA  — Moody's Seasoned Aaa Corporate Bond Yield (daily)   -> Graham revised formula "Y"
  DGS10 — 10-Year Treasury Constant Maturity Rate (daily)     -> Buffett discount rate, CAPM rf

Requires FRED_API_KEY. Without it (or on any failure) we return configured defaults and say so.
"""
from __future__ import annotations

import time
from dataclasses import dataclass

import httpx

from ..config import settings

FRED_URL = "https://api.stlouisfed.org/fred/series/observations"
_cache: dict[str, tuple[float, float, str]] = {}  # series -> (value, fetched_at, date)
_CACHE_TTL = 6 * 60 * 60


@dataclass
class Rates:
    aaa_yield_pct: float
    treasury_10y_pct: float
    source: str
    as_of: str | None

    def to_dict(self) -> dict:
        return {"aaa_yield_pct": self.aaa_yield_pct, "treasury_10y_pct": self.treasury_10y_pct,
                "source": self.source, "as_of": self.as_of}


async def _fred_latest(series: str, api_key: str) -> tuple[float, str] | None:
    hit = _cache.get(series)
    if hit and time.time() - hit[1] < _CACHE_TTL:
        return hit[0], hit[2]
    params = {"series_id": series, "api_key": api_key, "file_type": "json",
              "sort_order": "desc", "limit": 10}
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(FRED_URL, params=params)
        resp.raise_for_status()
        for obs in resp.json().get("observations", []):
            if obs.get("value") not in (None, ".", ""):
                val = float(obs["value"])
                _cache[series] = (val, time.time(), obs["date"])
                return val, obs["date"]
    except (httpx.HTTPError, ValueError, KeyError):
        return None
    return None


async def get_rates() -> Rates:
    defaults = Rates(settings.default_aaa_yield_pct, settings.default_treasury_10y_pct, "defaults", None)
    key = settings.fred_api_key
    if not key:
        return defaults
    aaa = await _fred_latest("DAAA", key)
    t10 = await _fred_latest("DGS10", key)
    if aaa is None or t10 is None:
        defaults.source = "defaults (FRED unavailable)"
        return defaults
    return Rates(aaa[0], t10[0], "FRED", max(aaa[1], t10[1]))
