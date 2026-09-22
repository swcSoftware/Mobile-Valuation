#!/usr/bin/env python3
"""
Concept-map coverage probe (Sprint 4, Track A).

Runs the reference normalizer over a universe of tickers and records, per company, which canonical
concepts resolved and which did not — with the tags that filer *does* report that may match. The
point is to find map gaps before users do, and to catch a filer renaming a tag between filings.

Writes:
  docs/coverage.json   machine-readable, committed so changes show up in git history
  docs/COVERAGE.md     human summary for the weekly review

Exits non-zero when a concept that resolved in the previous run no longer resolves (a regression),
so the scheduled workflow fails loudly instead of silently drifting.

Usage:
  SEC_USER_AGENT="Name email" .venv/bin/python ../../scripts/coverage_probe.py [universe.txt]
"""
from __future__ import annotations

import asyncio
import json
import os
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "services" / "valuation-engine"))

from valuation_engine.edgar.client import EdgarClient  # noqa: E402
from valuation_engine.edgar.companyfacts import fetch_companyfacts  # noqa: E402
from valuation_engine.edgar.tickers import resolve_ticker  # noqa: E402
from valuation_engine.errors import EngineError  # noqa: E402
from valuation_engine.normalize.tags import CONCEPTS  # noqa: E402
from valuation_engine.edgar.identity import profile as filer_profile  # noqa: E402
from valuation_engine.valuation.engine import load_financials  # noqa: E402

class _Ref:
    """CompanyRef-shaped view of normalized financials (the CIK may be a predecessor's)."""
    def __init__(self, fin):
        self.ticker, self.cik, self.name = fin.ticker, fin.cik, fin.name
    @property
    def cik_padded(self) -> str:
        return f"{self.cik:010d}"

STOPWORDS = {"of", "and", "the", "net", "total", "current", "noncurrent", "other", "common", "stock", "value",
             "amount", "attributable", "including", "excluding", "portion", "at", "carrying", "for", "to", "from", "in", "by", "per"}
SPLIT = re.compile(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

# Mirrors Sector.NOT_EXPECTED_BY_SECTOR in the core: concepts a filer of this type never reports.
NOT_EXPECTED = {
    "financial": {"capex", "d_and_a", "cost_of_revenue", "inventory", "current_assets", "current_liabilities", "operating_income"},
    "reit": {"cost_of_revenue", "inventory"},
    "general": set(),
}


def sector_mode(sic: str | None) -> str:
    try:
        code = int(sic or "")
    except ValueError:
        return "general"
    if 6020 <= code <= 6199 or code == 6712 or 6200 <= code <= 6299 or 6311 <= code <= 6411:
        return "financial"
    return "reit" if code == 6798 else "general"


def words(tag: str) -> list[str]:
    return [w.lower() for w in SPLIT.split(tag.split(":")[-1]) if len(w) > 2]


def stem(w: str) -> str:
    return w[:-1] if len(w) > 4 and w.endswith("s") and not w.endswith("ss") else w


def head(tag: str) -> str | None:
    """The head noun identifies the concept: InterestExpense is not revenue."""
    for w in words(tag):
        if w not in STOPWORDS:
            return stem(w)
    return None


def keywords(concept) -> set[str]:
    return {stem(w) for t in concept.tags for w in words(t)} - STOPWORDS


def plausible(cf, key: str, concept) -> bool:
    """A duration concept can't be satisfied by an instant fact, nor a USD one by a per-share unit."""
    want_duration = concept.kind.value == "flow" if hasattr(concept.kind, "value") else str(concept.kind).endswith("FLOW")
    for f in cf.facts.get(key, []):
        if (not f.is_instant) == want_duration and f.unit == concept.unit:
            return True
    return False


def candidates(cf, concept, limit: int = 6) -> list[str]:
    kw = keywords(concept)
    if not kw:
        return []
    mapped = {f"{concept.taxonomy}:{t}" for t in concept.tags}
    heads = {head(t) for t in concept.tags} - {None}
    scored = []
    for key in cf.facts:
        if key in mapped or head(key) not in heads or not plausible(cf, key, concept):
            continue
        hits = sum(1 for w in words(key) if stem(w) in kw)
        if hits:
            scored.append((hits, -len(key), key))
    scored.sort(reverse=True)
    return [k for _, _, k in scored[:limit]]


async def probe_one(client: EdgarClient, ticker: str) -> dict:
    try:
        # Same entry point the app uses, so successor-issuer resolution applies (XOM).
        fin = await load_financials(ticker, client.user_agent)
        cf = await fetch_companyfacts(client, await resolve_ticker(client, fin.ticker) if fin.cik == 0 else _Ref(fin))
    except EngineError as e:
        return {"ticker": ticker, "status": e.code, "message": str(e)[:160], "resolved": [], "gaps": []}
    except Exception as e:  # network/parse issues shouldn't abort the whole run
        return {"ticker": ticker, "status": "error", "message": f"{type(e).__name__}: {e}"[:160], "resolved": [], "gaps": []}

    prof = await filer_profile(client, fin.cik)
    mode = sector_mode(prof.sic if prof else None)
    not_expected = NOT_EXPECTED[mode]
    latest = fin.latest_annual
    ttm = fin.ttm
    resolved, gaps = [], []
    for c in CONCEPTS:
        if c.key == "shares_outstanding":
            continue
        in_annual = bool(latest and c.key in latest.values)
        in_ttm = bool(ttm and c.key in ttm.values)
        if in_annual or in_ttm:
            resolved.append(c.key)
            continue
        if c.requirement == "optional" or c.key in not_expected:
            continue        # legitimately absent for this filer / sector
        uses_mapped = any(cf.get(c.taxonomy, t) for t in c.tags)
        gaps.append({
            "concept": c.key,
            "kind": "unusable" if uses_mapped else "missing",
            "statement": c.statement,
            "critical": c.requirement == "required",
            "requirement": c.requirement,
            "candidates": [f"{c.taxonomy}:{t}" for t in c.tags if cf.get(c.taxonomy, t)] if uses_mapped else candidates(cf, c),
        })
    return {"ticker": ticker, "status": "ok", "cik": fin.cik, "company": fin.name, "sector": mode,
            "period": str(ttm.period_end if ttm else (latest.period_end if latest else "")),
            "resolved": resolved, "gaps": gaps}


async def main() -> None:
    ua = os.environ.get("SEC_USER_AGENT", "").strip()
    if not ua:
        raise SystemExit("SEC_USER_AGENT is not set (SEC fair-access policy requires 'Name email')")
    universe_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "scripts" / "universe.txt"
    tickers = [ln.strip() for ln in universe_path.read_text().splitlines() if ln.strip() and not ln.startswith("#")]

    client = EdgarClient(ua)
    results = []
    for t in tickers:
        results.append(await probe_one(client, t))
        print(f"  {results[-1]['ticker']:7s} {results[-1]['status']:18s} gaps={len(results[-1]['gaps'])}", flush=True)

    payload = {"generated": date.today().isoformat(), "universe": len(tickers), "results": results}
    out_json = ROOT / "docs" / "coverage.json"
    previous = json.loads(out_json.read_text())["results"] if out_json.exists() else []
    prev_resolved = {r["ticker"]: set(r.get("resolved", [])) for r in previous}

    regressions = []
    for r in results:
        lost = prev_resolved.get(r["ticker"], set()) - set(r.get("resolved", []))
        if lost:
            regressions.append((r["ticker"], sorted(lost)))

    out_json.write_text(json.dumps(payload, indent=1))
    write_markdown(payload, regressions)

    ok = [r for r in results if r["status"] == "ok"]
    crit = [(r["ticker"], g["concept"]) for r in ok for g in r["gaps"] if g["critical"]]
    print(f"\n{len(ok)}/{len(results)} valued · {len(crit)} critical gaps · {len(regressions)} regressions")
    if regressions:
        for t, lost in regressions:
            print(f"REGRESSION {t}: lost {', '.join(lost)}")
        raise SystemExit(1)


def write_markdown(payload: dict, regressions: list) -> None:
    results = payload["results"]
    ok = [r for r in results if r["status"] == "ok"]
    failed = [r for r in results if r["status"] != "ok"]
    counts: dict[str, int] = {}
    for r in ok:
        for g in r["gaps"]:
            counts[g["concept"]] = counts.get(g["concept"], 0) + 1

    lines = [
        "# Concept-map coverage",
        "",
        f"Generated {payload['generated']} by `scripts/coverage_probe.py` over {payload['universe']} tickers "
        f"(`scripts/universe.txt`), sector-aware. Regenerate after any change to the tag map; the weekly workflow fails if a "
        "company loses a concept it previously resolved.",
        "",
        f"**{len(ok)} valued · {len(failed)} not valued · {len(regressions)} regressions**",
        "",
    ]
    if regressions:
        lines += ["## ⚠ Regressions (concepts lost since the last run)", ""]
        lines += [f"- **{t}**: {', '.join(lost)}" for t, lost in regressions] + [""]

    lines += ["## Most frequently unresolved concepts", "", "| Concept | Companies | Critical |", "|---|---|---|"]
    crit_keys = {g["concept"] for r in ok for g in r["gaps"] if g["critical"]}
    for concept, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        lines.append(f"| `{concept}` | {n} | {'yes' if concept in crit_keys else ''} |")

    crit_rows = [(r, g) for r in ok for g in r["gaps"] if g["critical"]]
    lines += ["", "## Critical gaps (a model input is unavailable)", ""]
    if not crit_rows:
        lines.append("None.")
    for r, g in crit_rows:
        lines.append(f"- **{r['ticker']}** ({r['company']}, CIK {r['cik']}, {r.get('sector', '?')}) — `{g['concept']}` {g['kind']}"
                     + (f"; candidates: {', '.join('`' + c + '`' for c in g['candidates'][:3])}" if g["candidates"] else "; no related tags"))

    lines += ["", "## Not valued", ""]
    if not failed:
        lines.append("None.")
    for r in failed:
        lines.append(f"- **{r['ticker']}** — `{r['status']}`: {r['message']}")

    lines += ["", "---", "", "A similar tag name is not the same meaning. Before mapping anything here, read "
              "`docs/DATA_VERIFICATION.md` and update **both** `Concepts.kt` and `tags.py`, then regenerate the oracle."]
    (ROOT / "docs" / "COVERAGE.md").write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    asyncio.run(main())
