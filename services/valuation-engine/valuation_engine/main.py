"""ValueLens valuation engine — FastAPI entry point."""
from __future__ import annotations

import logging
import socket
from typing import Annotated

from fastapi import FastAPI, Header, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from . import __version__
from .config import settings
from .edgar.client import EdgarClient, cache
from .edgar.tickers import search_companies
from .errors import EngineError, UpstreamUnavailable
from .middleware import AccessMiddleware
from .providers.prices import configured_providers
from .providers.rates import get_rates
from .valuation.engine import build_valuation, financials_payload, load_financials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="ValueLens Valuation Engine",
    version=__version__,
    description="SEC EDGAR-backed fundamental valuation. No technical analysis.",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.add_middleware(AccessMiddleware)

UserAgent = Annotated[str | None, Header(alias="X-SEC-User-Agent")]


@app.exception_handler(EngineError)
async def engine_error_handler(_: Request, exc: EngineError) -> JSONResponse:
    return JSONResponse(exc.to_dict(), status_code=exc.status)


@app.exception_handler(Exception)
async def unexpected_error_handler(_: Request, exc: Exception) -> JSONResponse:
    logging.getLogger("valuation_engine").exception("unhandled")
    err = UpstreamUnavailable(f"Unexpected engine failure: {exc.__class__.__name__}")
    err.code = "engine_error"
    return JSONResponse(err.to_dict(), status_code=500)


def _lan_addresses() -> list[str]:
    """Best-effort local IPs so a physical phone on the same Wi-Fi can find the engine."""
    out: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                out.add(ip)
    except socket.gaierror:
        pass
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        out.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    return sorted(out)


@app.get("/health")
async def health() -> dict:
    rates = await get_rates()
    return {
        "status": "ok",
        "version": __version__,
        "rates": rates.to_dict(),
        "price_providers": configured_providers(),
        "cache": cache().stats(),
        "require_identity": settings.require_identity,
        "client_rate_limit_per_minute": settings.client_rate_limit_per_minute,
        "lan_addresses": _lan_addresses(),
    }


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
    return financials_payload(await load_financials(ticker, x_sec_user_agent))


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
    return await build_valuation(ticker, x_sec_user_agent, overrides, price)
