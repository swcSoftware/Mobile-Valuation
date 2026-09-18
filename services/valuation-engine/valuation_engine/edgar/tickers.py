"""Ticker -> CIK resolution using SEC's published master list."""
from __future__ import annotations

from dataclasses import dataclass

from .client import EdgarClient, EdgarNotFound

TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"


@dataclass(frozen=True)
class CompanyRef:
    ticker: str
    cik: int
    name: str

    @property
    def cik_padded(self) -> str:
        return f"{self.cik:010d}"


def _normalize_ticker(raw: str) -> str:
    # SEC uses "BRK-B"; users type "BRK.B" or "brk b".
    return raw.strip().upper().replace(".", "-").replace(" ", "-")


async def load_master_list(client: EdgarClient) -> list[CompanyRef]:
    data = await client.get_json(TICKERS_URL)
    return [
        CompanyRef(ticker=row["ticker"], cik=int(row["cik_str"]), name=row["title"])
        for row in data.values()
    ]


async def resolve_ticker(client: EdgarClient, ticker: str) -> CompanyRef:
    wanted = _normalize_ticker(ticker)
    for ref in await load_master_list(client):
        if ref.ticker == wanted:
            return ref
    raise EdgarNotFound(f"Unknown ticker: {ticker}")


async def search_companies(client: EdgarClient, query: str, limit: int = 15) -> list[CompanyRef]:
    q = query.strip().upper()
    if not q:
        return []
    refs = await load_master_list(client)
    exact = [r for r in refs if r.ticker == _normalize_ticker(q)]
    prefix = [r for r in refs if r.ticker.startswith(q) and r not in exact]
    name = [r for r in refs if q in r.name.upper() and r not in exact and r not in prefix]
    return (exact + prefix + name)[:limit]
