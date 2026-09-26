"""
Fill a companyfacts lag from the filing itself (ISSUES #53) — mirror of packages/valuation-core GapFill.kt.

SEC's companyfacts feed sometimes goes months without ingesting a filing: Coca-Cola's 10-Q filed
2026-07-29 was still absent in late September, so the trailing twelve months stopped in April (5 of 77
universe filers were a quarter behind). The filing's own XBRL instance has the numbers.

Entered only when the filer's submissions list a 10-Q/10-K filed after anything in companyfacts;
every other filer returns immediately, fetching nothing. Reads undimensioned facts for mapped tags
only — exactly what companyfacts itself would have carried — and adds them as facts of that filing,
so the normalizer treats them as if SEC had caught up. Always says so in a note.
"""
from __future__ import annotations

import re
from datetime import date

from ..normalize.tags import CONCEPTS
from .client import EdgarClient
from .companyfacts import CompanyFacts, Fact
from .identity import submissions_url

FILING_FORMS = ("10-Q", "10-K")
_CTX = re.compile(r'<(?:xbrli:)?context\s+id="([^"]+)"\s*>(.*?)</(?:xbrli:)?context>', re.S)
_UNIT = re.compile(r'<(?:xbrli:)?unit\s+id="([^"]+)"\s*>(.*?)</(?:xbrli:)?unit>', re.S)
_START = re.compile(r"<(?:xbrli:)?startDate>([^<]+)<")
_END = re.compile(r"<(?:xbrli:)?endDate>([^<]+)<")
_INSTANT = re.compile(r"<(?:xbrli:)?instant>([^<]+)<")
_MEASURE = re.compile(r"<(?:xbrli:)?measure>([^<]+)<")
_FACT = re.compile(r"<(us-gaap|dei):([A-Za-z0-9_]+)\s([^>]*)>([^<]*)</")
_ATTR = re.compile(r'(contextRef|unitRef)="([^"]+)"')


def latest_filing(submissions: dict) -> dict | None:
    """Newest 10-Q/10-K in the submissions JSON (listed newest first)."""
    r = submissions.get("filings", {}).get("recent", {})
    for i, form in enumerate(r.get("form", [])):
        if form in FILING_FORMS:
            return {"accession": r["accessionNumber"][i], "primary": r["primaryDocument"][i], "form": form,
                    "filed": date.fromisoformat(r["filingDate"][i]), "period": r.get("reportDate", [""] * (i + 1))[i]}
    return None


def latest_filed(cf: CompanyFacts) -> date | None:
    filed = [f.filed for facts in cf.facts.values() for f in facts if f.form.startswith(FILING_FORMS)]
    return max(filed) if filed else None


def needs_fill(cf: CompanyFacts, filing: dict | None) -> bool:
    if filing is None:
        return False
    newest = latest_filed(cf)
    if newest is None or filing["filed"] <= newest:
        return False
    return not any(f.accession == filing["accession"] for facts in cf.facts.values() for f in facts)


def instance_url(cik: int, filing: dict) -> str:
    base = filing["primary"].rsplit(".", 1)[0]
    return f"https://www.sec.gov/Archives/edgar/data/{cik}/{filing['accession'].replace('-', '')}/{base}_htm.xml"


def _unit(body: str) -> str | None:
    measures = [m.split(":")[-1] for m in _MEASURE.findall(body)]
    if "divide" in body:
        return "USD/shares" if measures == ["USD", "shares"] else None
    return {"USD": "USD", "shares": "shares"}.get(measures[0]) if len(measures) == 1 else None


def parse_instance(xml: str, filing: dict) -> list[Fact]:
    """Undimensioned facts for tags the concept map reads, as Facts of `filing`."""
    wanted = {(c.taxonomy, t) for c in CONCEPTS for t in c.tags}
    contexts: dict[str, tuple[date | None, date]] = {}
    for cid, body in _CTX.findall(xml):
        if "segment" in body or "scenario" in body:
            continue      # dimensioned: companyfacts drops these too
        end = _END.search(body) or _INSTANT.search(body)
        start = _START.search(body)
        if end:
            contexts[cid] = (date.fromisoformat(start.group(1).strip()) if start else None, date.fromisoformat(end.group(1).strip()))
    units = {uid: _unit(body) for uid, body in _UNIT.findall(xml)}
    out: dict[tuple, Fact] = {}
    for taxonomy, tag, attrs, text in _FACT.findall(xml):
        if (taxonomy, tag) not in wanted:
            continue
        a = dict(_ATTR.findall(attrs))
        ctx, unit = contexts.get(a.get("contextRef", "")), units.get(a.get("unitRef", ""))
        if ctx is None or unit is None:
            continue
        try:
            value = float(text.strip())
        except ValueError:
            continue
        start, end = ctx
        out.setdefault((taxonomy, tag, unit, start, end), Fact(
            taxonomy=taxonomy, tag=tag, unit=unit, value=value, end=end, start=start, form=filing["form"],
            fp=None, fy=None, accession=filing["accession"], filed=filing["filed"], frame=None))
    return list(out.values())


def merge(cf: CompanyFacts, extra: list[Fact]) -> CompanyFacts:
    facts = {k: list(v) for k, v in cf.facts.items()}
    for f in extra:
        facts.setdefault(f"{f.taxonomy}:{f.tag}", []).append(f)
    return CompanyFacts(ref=cf.ref, entity_name=cf.entity_name, facts=facts)


def note_for(filing: dict, n: int) -> str:
    period = f", period ending {filing['period']}" if filing.get("period") else ""
    return (f"SEC's companyfacts feed has not yet published the {filing['form']} filed {filing['filed']}{period}; "
            f"{n} figures were read directly from that filing's XBRL.")


async def fill(client: EdgarClient, cf: CompanyFacts) -> tuple[CompanyFacts, str | None]:
    """Returns `cf` itself (untouched, nothing fetched) unless companyfacts lags the filer's latest filing."""
    try:
        filing = latest_filing(await client.get_json(submissions_url(cf.ref.cik)))
    except Exception:
        return cf, None
    if not needs_fill(cf, filing):
        return cf, None
    xml = await client.get_text(instance_url(cf.ref.cik, filing))
    if not xml or "<xbrl" not in xml and "<xbrli:xbrl" not in xml:
        return cf, None
    extra = parse_instance(xml, filing)
    if not extra:
        return cf, None
    return merge(cf, extra), note_for(filing, len(extra))
