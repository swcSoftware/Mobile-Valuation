import pytest
import respx
from httpx import Response

from valuation_engine.providers import prices
from valuation_engine.providers.prices import PolygonProvider, YahooChartProvider, get_quote


@respx.mock
@pytest.mark.asyncio
async def test_polygon_provider_parses_prev_close():
    respx.get(url__regex=r"https://api\.polygon\.io/v2/aggs/ticker/BRK\.B/prev.*").mock(
        return_value=Response(200, json={"results": [{"c": 456.78, "t": 1789700000000}]}))
    q = await PolygonProvider("k").quote("BRK-B")
    assert q.price == 456.78 and q.source == "polygon" and q.ticker == "BRK-B"


@respx.mock
@pytest.mark.asyncio
async def test_polygon_first_when_configured(monkeypatch):
    monkeypatch.setattr(prices.settings, "polygon_api_key", "k")
    respx.get(url__regex=r"https://api\.polygon\.io/.*").mock(return_value=Response(200, json={"results": [{"c": 1.0}]}))
    q = await get_quote("AAPL")
    assert q.source == "polygon"


@respx.mock
@pytest.mark.asyncio
async def test_yahoo_failure_falls_through(monkeypatch):
    monkeypatch.setattr(prices.settings, "polygon_api_key", None)
    respx.get(url__regex=r"https://query1\.finance\.yahoo\.com/.*").mock(return_value=Response(500))
    respx.get(url__regex=r"https://stooq\.com/.*").mock(
        return_value=Response(200, text="Symbol,Date,Time,Open,High,Low,Close,Volume\nAAPL.US,2026-09-18,22:00:00,1,2,0.5,1.5,10\n"))
    q = await get_quote("AAPL")
    assert q.source == "stooq" and q.price == 1.5


@respx.mock
@pytest.mark.asyncio
async def test_all_providers_fail_returns_none(monkeypatch):
    monkeypatch.setattr(prices.settings, "polygon_api_key", None)
    respx.get(url__regex=r".*").mock(return_value=Response(500))
    assert await get_quote("AAPL") is None
