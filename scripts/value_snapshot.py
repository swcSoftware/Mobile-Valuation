#!/usr/bin/env python3
"""
Blast-radius evidence for a normalization change (CLAUDE.md non-negotiable 4).

Snapshots every normalized value (annual + TTM), the current share count and the warnings for
every ticker in the universe, then diffs two snapshots. Run it once before a change and once after:

  cd services/valuation-engine
  SEC_USER_AGENT="Name email" .venv/bin/python ../../scripts/value_snapshot.py snap /tmp/before.json
  # … make the change …
  SEC_USER_AGENT="Name email" .venv/bin/python ../../scripts/value_snapshot.py snap /tmp/after.json
  .venv/bin/python ../../scripts/value_snapshot.py diff /tmp/before.json /tmp/after.json

The diff prints "N of M tickers affected" and every value that appeared, disappeared or moved.
companyfacts is cached for a day (.cache/edgar.sqlite), so the second run makes no SEC requests.
"""
from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "services" / "valuation-engine"))

from valuation_engine.errors import EngineError  # noqa: E402
from valuation_engine.valuation.engine import load_financials  # noqa: E402


def _period(p) -> dict:
    return {k: {"value": v.value, "tag": f"{v.taxonomy}:{v.tag}", "end": v.period_end.isoformat()} for k, v in p.values.items()}


async def snap(out: Path, universe: Path) -> None:
    ua = os.environ.get("SEC_USER_AGENT", "").strip()
    if not ua:
        raise SystemExit("SEC_USER_AGENT is not set (SEC fair-access policy requires 'Name email')")
    tickers = [ln.strip() for ln in universe.read_text().splitlines() if ln.strip() and not ln.startswith("#")]
    result: dict[str, dict] = {}
    for t in tickers:
        try:
            fin = await load_financials(t, ua)
        except EngineError as e:
            result[t] = {"error": type(e).__name__}
            continue
        result[t] = {
            "annual": {p.label: _period(p) for p in fin.annual},
            "ttm": _period(fin.ttm) if fin.ttm else None,
            "current_shares": fin.current_shares.value if fin.current_shares else None,
            "warnings": fin.warnings,
        }
        print(f"  {t}", flush=True)
    out.write_text(json.dumps(result, indent=1, sort_keys=True))
    print(f"{len(result)} tickers → {out}")


def _flatten(entry: dict) -> dict[str, object]:
    flat: dict[str, object] = {}
    if "error" in entry:
        return {"error": entry["error"]}
    for label, values in entry["annual"].items():
        for k, v in values.items():
            flat[f"{label}.{k}"] = (round(v["value"], 6), v["tag"])
    for k, v in (entry["ttm"] or {}).items():
        flat[f"TTM.{k}"] = (round(v["value"], 6), v["tag"], v["end"])
    flat["current_shares"] = entry["current_shares"]
    for w in entry["warnings"]:
        flat[f"warning: {w}"] = True
    return flat


def diff(a_path: Path, b_path: Path) -> None:
    a, b = json.loads(a_path.read_text()), json.loads(b_path.read_text())
    affected = []
    for t in sorted(set(a) | set(b)):
        fa, fb = _flatten(a.get(t, {"error": "absent"})), _flatten(b.get(t, {"error": "absent"}))
        changes = [(k, fa.get(k), fb.get(k)) for k in sorted(set(fa) | set(fb)) if fa.get(k) != fb.get(k)]
        if changes:
            affected.append(t)
            print(f"\n{t}: {len(changes)} change(s)")
            for k, before, after in changes:
                print(f"  {k}\n    before: {before}\n    after:  {after}")
    print(f"\n{len(affected)} of {len(set(a) | set(b))} tickers affected" + (f": {', '.join(affected)}" if affected else ""))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "snap":
        asyncio.run(snap(Path(sys.argv[2]), Path(sys.argv[3]) if len(sys.argv) > 3 else ROOT / "scripts" / "universe.txt"))
    elif len(sys.argv) == 4 and sys.argv[1] == "diff":
        diff(Path(sys.argv[2]), Path(sys.argv[3]))
    else:
        raise SystemExit(__doc__)
