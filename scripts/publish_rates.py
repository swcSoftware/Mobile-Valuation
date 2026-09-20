#!/usr/bin/env python3
"""
Fetch FRED DAAA (Moody's Aaa corporate yield) and DGS10 (10-yr Treasury) and write rates.json —
the keyless file the apps read from GitHub Pages. The FRED key never leaves CI.

Usage:  FRED_API_KEY=... python3 scripts/publish_rates.py [out_dir]
Output: <out_dir>/rates.json   (default: ./site)
Exit 1 on any failure so the workflow does NOT overwrite a good file with a bad one.
"""
import json
import os
import sys
import urllib.parse
import ssl
import urllib.request


def _ssl_context() -> ssl.SSLContext:
    """System certs on CI; python.org macOS builds may need certifi."""
    try:
        import certifi  # type: ignore
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()
from datetime import datetime, timezone
from pathlib import Path

FRED = "https://api.stlouisfed.org/fred/series/observations"


def observations(series: str, key: str, limit: int = 45) -> list[tuple[str, float]]:
    q = urllib.parse.urlencode({"series_id": series, "api_key": key, "file_type": "json", "sort_order": "desc", "limit": limit})
    with urllib.request.urlopen(f"{FRED}?{q}", timeout=30, context=_ssl_context()) as r:
        body = json.load(r)
    out = []
    for o in body.get("observations", []):
        if o.get("value") not in (None, ".", ""):
            out.append((o["date"], float(o["value"])))
    if not out:
        raise SystemExit(f"FRED returned no usable observations for {series}")
    return out  # newest first


def main() -> None:
    key = os.environ.get("FRED_API_KEY", "").strip()
    if not key:
        raise SystemExit("FRED_API_KEY is not set")
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "site")
    out_dir.mkdir(parents=True, exist_ok=True)

    aaa = observations("DAAA", key)
    t10 = observations("DGS10", key)
    by_date: dict[str, dict[str, float]] = {}
    for d, v in aaa:
        by_date.setdefault(d, {})["aaa"] = v
    for d, v in t10:
        by_date.setdefault(d, {})["t10"] = v
    history = [{"date": d, "aaa": vals.get("aaa"), "t10": vals.get("t10")} for d, vals in sorted(by_date.items(), reverse=True)][:30]

    payload = {
        "as_of": max(aaa[0][0], t10[0][0]),
        "published_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "aaa_yield_pct": aaa[0][1],
        "treasury_10y_pct": t10[0][1],
        "source": "FRED DAAA / DGS10",
        "history": history,
    }
    # Sanity: bond yields outside 0–20% mean something is wrong upstream.
    for k in ("aaa_yield_pct", "treasury_10y_pct"):
        if not 0 < payload[k] < 20:
            raise SystemExit(f"{k} = {payload[k]} is implausible; refusing to publish")
    (out_dir / "rates.json").write_text(json.dumps(payload, indent=1))
    print(f"rates.json: as_of {payload['as_of']} AAA {payload['aaa_yield_pct']} 10Y {payload['treasury_10y_pct']}")


if __name__ == "__main__":
    main()
