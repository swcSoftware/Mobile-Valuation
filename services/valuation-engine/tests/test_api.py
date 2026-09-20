"""HTTP surface with SEC and price providers mocked (respx)."""
import json
from pathlib import Path

import pytest
import respx
from fastapi.testclient import TestClient
from httpx import Response

from valuation_engine import main as main_module
from valuation_engine.config import settings
from valuation_engine.edgar.client import cache
from valuation_engine.middleware import ClientRateLimiter, limiter

FIX = Path(__file__).parent / "fixtures"
UA = {"X-SEC-User-Agent": "Test Person test@example.com"}


@pytest.fixture(autouse=True)
def clean_cache_and_limiter():
    cache()._conn.execute("DELETE FROM cache")
    cache()._conn.commit()
    limiter._hits.clear()
    yield


@pytest.fixture
def client():
    return TestClient(main_module.app, raise_server_exceptions=False)


def mock_sec(router: respx.MockRouter, tickers=("AAPL",)):
    router.get("https://www.sec.gov/files/company_tickers.json").mock(
        return_value=Response(200, text=(FIX / "company_tickers.json").read_text()))
    for t in tickers:
        raw = json.loads((FIX / f"companyfacts_{t}.json").read_text())
        router.get(f"https://data.sec.gov/api/xbrl/companyfacts/CIK{raw['cik']:010d}.json").mock(
            return_value=Response(200, json=raw))
    router.get(url__regex=r"https://query1\.finance\.yahoo\.com/.*").mock(
        return_value=Response(200, json={"chart": {"result": [{"meta": {"regularMarketPrice": 123.45, "currency": "USD", "regularMarketTime": 1789761602}}]}}))
    router.get(url__regex=r"https://stooq\.com/.*").mock(return_value=Response(404))


@respx.mock
def test_valuation_happy_path(client):
    mock_sec(respx)
    r = client.get("/companies/AAPL/valuation", headers=UA)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["company"]["ticker"] == "AAPL"
    assert body["quote"]["price"] == 123.45 and body["quote"]["source"] == "yahoo"
    assert body["model_a"]["margin_of_safety"]["market_price"] == 123.45
    assert len(body["history"]) == 10
    # Second call must be served from the SQLite cache (no new SEC calls).
    calls_before = respx.calls.call_count
    client.get("/companies/AAPL/valuation", headers=UA)
    sec_calls = [c for c in respx.calls[calls_before:] if "sec.gov" in str(c.request.url)]
    assert not sec_calls


@respx.mock
def test_price_override_and_assumption_override(client):
    mock_sec(respx)
    r = client.get("/companies/AAPL/valuation?price=50&hurdle_rate_pct=8", headers=UA)
    body = r.json()
    assert body["quote"]["source"] == "manual" and body["quote"]["price"] == 50
    assert body["assumptions"]["hurdle_rate_pct"] == 8


@respx.mock
def test_unknown_ticker_error_shape(client):
    mock_sec(respx)
    r = client.get("/companies/NOPE/valuation", headers=UA)
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "unknown_ticker"
    assert "detail" in r.json()  # Sprint-0 compatibility


@respx.mock
def test_upstream_unavailable(client):
    respx.get("https://www.sec.gov/files/company_tickers.json").mock(return_value=Response(503))
    r = client.get("/search?q=AAPL", headers=UA)
    assert r.status_code == 503
    assert r.json()["error"]["code"] == "upstream_unavailable"


@respx.mock
def test_search(client):
    mock_sec(respx)
    r = client.get("/search?q=app", headers=UA)
    assert r.status_code == 200
    assert r.json()["results"][0]["ticker"] == "AAPL"


def test_rate_limit(client, monkeypatch):
    monkeypatch.setattr(limiter, "per_minute", 3)
    for _ in range(3):
        assert client.get("/market/rates", headers=UA).status_code == 200
    r = client.get("/market/rates", headers=UA)
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "rate_limited"
    assert "Retry-After" in r.headers
    # a different identity is not affected
    assert client.get("/market/rates", headers={"X-SEC-User-Agent": "Other Person o@example.com"}).status_code == 200


def test_identity_required(client, monkeypatch):
    monkeypatch.setattr(settings, "require_identity", True)
    assert client.get("/market/rates").status_code == 400
    assert client.get("/market/rates", headers={"X-SEC-User-Agent": "bot"}).status_code == 400
    assert client.get("/market/rates", headers=UA).status_code == 200
    assert client.get("/health").status_code == 200  # always open


def test_client_rate_limiter_window():
    lim = ClientRateLimiter(2)
    assert lim.check("a") == (True, 0)
    assert lim.check("a") == (True, 0)
    ok, retry = lim.check("a")
    assert not ok and 0 < retry <= 61
