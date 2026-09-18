"""
Market price providers. SEC EDGAR carries no prices, so this is the one non-SEC input.

Pluggable: implement `PriceProvider.quote(ticker)`; the engine tries providers in order
and reports which one supplied the number. A client-supplied override always wins.
"""
from __future__ import annotations

import csv
import io
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Protocol

import httpx


@dataclass
class Quote:
    ticker: str
    price: float
    currency: str
    as_of: str
    source: str

    def to_dict(self) -> dict:
        return {"ticker": self.ticker, "price": self.price, "currency": self.currency,
                "as_of": self.as_of, "source": self.source}


class PriceProvider(Protocol):
    name: str
    async def quote(self, ticker: str) -> Quote | None: ...


class StooqProvider:
    """Free, key-less, unofficial. Delayed quotes; good enough for an alpha."""
    name = "stooq"
    URL = "https://stooq.com/q/l/?s={symbol}&f=sd2t2ohlcv&h&e=csv"

    async def quote(self, ticker: str) -> Quote | None:
        symbol = ticker.lower().replace(".", "-") + ".us"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(self.URL.format(symbol=symbol), headers={"User-Agent": "ValueLens/0.1"})
            resp.raise_for_status()
        except httpx.HTTPError:
            return None
        rows = list(csv.DictReader(io.StringIO(resp.text)))
        if not rows:
            return None
        row = rows[0]
        try:
            price = float(row["Close"])
        except (KeyError, ValueError):
            return None
        as_of = f"{row.get('Date', '')} {row.get('Time', '')}".strip() or datetime.now(timezone.utc).isoformat()
        return Quote(ticker=ticker.upper(), price=price, currency="USD", as_of=as_of, source=self.name)


class YahooChartProvider:
    """Unofficial Yahoo Finance chart endpoint. No key; may change without notice."""
    name = "yahoo"
    URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d"

    async def quote(self, ticker: str) -> Quote | None:
        symbol = ticker.upper().replace(".", "-")
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(self.URL.format(symbol=symbol), headers={"User-Agent": "Mozilla/5.0 ValueLens/0.1"})
            resp.raise_for_status()
            meta = resp.json()["chart"]["result"][0]["meta"]
            price = float(meta["regularMarketPrice"])
            ts = meta.get("regularMarketTime")
            as_of = datetime.fromtimestamp(ts, tz=timezone.utc).isoformat() if ts else datetime.now(timezone.utc).isoformat()
            return Quote(ticker=ticker.upper(), price=price, currency=meta.get("currency", "USD"), as_of=as_of, source=self.name)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError):
            return None


class ManualProvider:
    name = "manual"

    def __init__(self, price: float) -> None:
        self._price = price

    async def quote(self, ticker: str) -> Quote | None:
        return Quote(ticker=ticker.upper(), price=self._price, currency="USD",
                     as_of=datetime.now(timezone.utc).isoformat(), source=self.name)


async def get_quote(ticker: str, override: float | None = None) -> Quote | None:
    providers: list[PriceProvider] = []
    if override is not None:
        providers.append(ManualProvider(override))
    providers.append(YahooChartProvider())
    providers.append(StooqProvider())
    for p in providers:
        q = await p.quote(ticker)
        if q is not None:
            return q
    return None
