"""ValueLens valuation engine — FastAPI entry point."""
from __future__ import annotations

from typing import Annotated

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from . import __version__
from .edgar.client import EdgarClient, EdgarNotFound
from .edgar.tickers import search_companies
from .providers.rates import get_rates
from .valuation.engine import build_valuation, financials_payload, load_financials

app = FastAPI(
    title="ValueLens Valuation Engine",
    version=__version__,
    description="SEC EDGAR-backed fundamental valuation. No technical analysis.",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

UserAgent = Annotated[str | None, Header(alias="X-SEC-User-Agent")]


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "version": __version__}


@app.get("/search")
async def search(q: str, x_sec_user_agent: UserAgent = None, limit: int = 15) -> dict:
    client = EdgarClient(x_sec_user_agent)
    refs = await search_companies(client, q, limit=limit)
    return {"results": [{"ticker": r.ticker, "cik": r.cik, "name": r.name} for r in refs]}


@app.get("/market/rates")
async def market_rates() -> dict:
    return (await get_rates()).to_dict()


@app.get("/companies/{ticker}/financials")
async def financials(ticker: str, x_sec_user_agent: UserAgent = None) -> dict:
    try:
        fin = await load_financials(ticker, x_sec_user_agent)
    except EdgarNotFound as e:
        raise HTTPException(404, str(e))
    return financials_payload(fin)


@app.get("/companies/{ticker}/valuation")
async def valuation(
    ticker: str,
    x_sec_user_agent: UserAgent = None,
    price: Annotated[float | None, Query(description="Override market price")] = None,
    aaa_yield_pct: float | None = None,
    treasury_10y_pct: float | None = None,
    hurdle_rate_pct: float | None = None,
    equity_risk_premium_pct: float | None = None,
    beta: float | None = None,
    terminal_growth_pct: float | None = None,
    exit_multiple: float | None = None,
    tax_rate_pct: float | None = None,
) -> dict:
    overrides = {
        "aaa_yield_pct": aaa_yield_pct, "treasury_10y_pct": treasury_10y_pct,
        "hurdle_rate_pct": hurdle_rate_pct, "equity_risk_premium_pct": equity_risk_premium_pct,
        "beta": beta, "terminal_growth_pct": terminal_growth_pct, "exit_multiple": exit_multiple,
        "tax_rate_pct": tax_rate_pct,
    }
    try:
        return await build_valuation(ticker, x_sec_user_agent, overrides, price)
    except EdgarNotFound as e:
        raise HTTPException(404, str(e))
