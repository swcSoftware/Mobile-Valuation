import json
import os
import tempfile
from pathlib import Path

# Isolate the test cache from the developer's live SQLite cache (must run before engine imports).
os.environ.setdefault("CACHE_DB", os.path.join(tempfile.mkdtemp(prefix="valuelens-test-"), "edgar.sqlite"))
os.environ.setdefault("FRED_API_KEY", "")
os.environ.setdefault("POLYGON_API_KEY", "")

import pytest

from valuation_engine.edgar.companyfacts import parse_companyfacts
from valuation_engine.edgar.tickers import CompanyRef
from valuation_engine.normalize.statements import normalize

FIXTURES = Path(__file__).parent / "fixtures"


def load_raw(ticker: str) -> dict:
    return json.loads((FIXTURES / f"companyfacts_{ticker}.json").read_text())


def load_fin(ticker: str):
    raw = load_raw(ticker)
    ref = CompanyRef(ticker=ticker, cik=raw["cik"], name=raw["entityName"])
    return normalize(parse_companyfacts(raw, ref))


@pytest.fixture(scope="session")
def aapl():
    return load_fin("AAPL")


@pytest.fixture(scope="session")
def ko():
    return load_fin("KO")


@pytest.fixture(scope="session")
def jnj():
    return load_fin("JNJ")
