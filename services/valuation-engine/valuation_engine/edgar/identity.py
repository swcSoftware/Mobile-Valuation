"""
Successor-issuer resolution — mirror of packages/valuation-core FilerIdentity.kt.

When a company reorganizes into a new holding company (Rule 12g-3, form 8-K12B) SEC's ticker
list points at the NEW CIK with no 10-K history (XOM → ExxonMobil Holdings Corp, CIK 2115436).
Runs only when the primary path finds no annual data. A predecessor is accepted on evidence:
same SIC, files 10-Ks, latest 10-K precedes the successor's first filing and is recent.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from .client import EdgarClient
from .tickers import CompanyRef

SUFFIXES = {"THE", "HOLDINGS", "HOLDING", "CORP", "CORPORATION", "INC", "CO", "COMPANY", "LTD", "PLC", "GROUP", "LLC", "SA", "NV", "AG"}
MAX_PREDECESSOR_10K_AGE_DAYS = 550


@dataclass
class FilerProfile:
    cik: int
    name: str
    sic: str | None
    first_filing: date | None
    latest_10k: date | None
    has_successor_notice: bool
    forms: frozenset[str] = frozenset()

    @property
    def is_foreign_private_issuer(self) -> bool:
        return any(f.startswith(("20-F", "40-F")) for f in self.forms)

    @property
    def is_new_listing(self) -> bool:
        return any(f in ("S-1", "424B4", "S-11") or f.startswith("F-1") for f in self.forms)

    @property
    def is_fund(self) -> bool:
        return any(f.startswith(("N-CSR", "N-1A")) or f == "N-2" for f in self.forms)


@dataclass
class Predecessor:
    ref: CompanyRef
    successor_name: str
    successor_first_filing: date | None
    via_successor_notice: bool


def submissions_url(cik: int) -> str:
    return f"https://data.sec.gov/submissions/CIK{cik:010d}.json"


def entity_search_url(prefix: str) -> str:
    return "https://efts.sec.gov/LATEST/search-index?keysTyped=" + prefix.lower().replace(" ", "%20")


def search_token(name: str) -> str | None:
    for w in name.upper().replace(",", " ").replace(".", " ").split():
        if w and w not in SUFFIXES:
            return w.lower()
    return None


def search_prefixes(token: str) -> list[str]:
    out = []
    for p in (token, token[:5], token[:4]):
        if len(p) >= 4 and p not in out:
            out.append(p)
    return out


def parse_profile(raw: dict) -> FilerProfile:
    recent = raw.get("filings", {}).get("recent", {})
    forms, dates = recent.get("form", []), recent.get("filingDate", [])
    ten_k = [date.fromisoformat(d) for f, d in zip(forms, dates) if f == "10-K"]
    all_dates = [date.fromisoformat(d) for d in dates]
    return FilerProfile(
        cik=int(raw["cik"]), name=raw.get("name", ""), sic=(raw.get("sic") or None),
        first_filing=min(all_dates) if all_dates else None, latest_10k=max(ten_k) if ten_k else None,
        has_successor_notice=any(f.startswith(("8-K12B", "8-K12G3")) for f in forms),
        forms=frozenset(forms),
    )


def no_annual_data_reason(ticker: str, p: FilerProfile | None) -> str:
    if p is None:
        return f"{ticker} has no 10-K income statement data on EDGAR (foreign filer, fund, SPAC or new listing)."
    if p.is_foreign_private_issuer:
        return f"{ticker} ({p.name}) is a foreign private issuer that files 20-F/40-F reports, which ValueLens doesn't parse yet."
    if p.is_fund:
        return f"{ticker} ({p.name}) is a fund or trust, not an operating company; it has no 10-K to value."
    if p.is_new_listing and p.latest_10k is None:
        first = f" (first SEC filing {p.first_filing})" if p.first_filing else ""
        return f"{ticker} ({p.name}) listed recently{first} and hasn't filed its first 10-K yet. Come back after its fiscal year-end report."
    if p.latest_10k is None:
        return f"{ticker} ({p.name}) has no 10-K on file with SEC."
    return f"{ticker} ({p.name}) files 10-Ks but none carry XBRL income-statement facts ValueLens can read."


def is_plausible_predecessor(successor: FilerProfile, candidate: FilerProfile, today: date) -> bool:
    """Substitution requires an 8-K12B/8-K12G3 on the successor; IPOs and spin-offs never substitute."""
    if not successor.has_successor_notice:
        return False
    if candidate.latest_10k is None:
        return False
    if successor.sic and candidate.sic and successor.sic != candidate.sic:
        return False
    if (today - candidate.latest_10k).days > MAX_PREDECESSOR_10K_AGE_DAYS:
        return False
    if successor.first_filing and candidate.latest_10k > successor.first_filing + timedelta(days=30):
        return False
    return True


async def profile(client: EdgarClient, cik: int) -> FilerProfile | None:
    try:
        return parse_profile(await client.get_json(submissions_url(cik)))
    except Exception:
        return None


async def find_predecessor(client: EdgarClient, successor: CompanyRef, today: date, sp: FilerProfile | None = None) -> Predecessor | None:
    sp = sp or await profile(client, successor.cik)
    if sp is None or not sp.has_successor_notice:
        return None
    token = search_token(sp.name or successor.name)
    if not token:
        return None
    seen: set[int] = set()
    for prefix in search_prefixes(token):
        try:
            hits = (await client.get_json(entity_search_url(prefix))).get("hits", {}).get("hits", [])
        except Exception:
            continue
        for h in hits[:8]:
            try:
                cik = int(h["_id"])
            except (KeyError, ValueError):
                continue
            if cik == successor.cik or cik in seen:
                continue
            seen.add(cik)
            cp = await profile(client, cik)
            if cp and is_plausible_predecessor(sp, cp, today):
                name = cp.name or h.get("_source", {}).get("entity", "").split(" (")[0]
                return Predecessor(CompanyRef(ticker=successor.ticker, cik=cik, name=name), sp.name, sp.first_filing, sp.has_successor_notice)
    return None
