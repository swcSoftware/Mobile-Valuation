#!/usr/bin/env python3
"""
Mirror SEC's company_tickers.json to GitHub Pages (weekly) so the apps can search instantly and
offline without every phone downloading ~700 KB from SEC on first launch.

Usage:  SEC_USER_AGENT="Name email" python3 scripts/publish_tickers.py [out_dir]
"""
import json
import os
import sys
import ssl
import urllib.request


def _ssl_context() -> ssl.SSLContext:
    """System certs on CI; python.org macOS builds may need certifi."""
    try:
        import certifi  # type: ignore
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()
from pathlib import Path

URL = "https://www.sec.gov/files/company_tickers.json"


def main() -> None:
    ua = os.environ.get("SEC_USER_AGENT", "").strip()
    if not ua:
        raise SystemExit("SEC_USER_AGENT is not set (SEC fair-access policy requires 'Name email')")
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "site")
    out_dir.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(URL, headers={"User-Agent": ua, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60, context=_ssl_context()) as r:
        data = json.load(r)
    if len(data) < 5000:
        raise SystemExit(f"Only {len(data)} tickers; refusing to publish a truncated list")
    (out_dir / "tickers.json").write_text(json.dumps(data, separators=(",", ":")))
    print(f"tickers.json: {len(data)} filers")


if __name__ == "__main__":
    main()
