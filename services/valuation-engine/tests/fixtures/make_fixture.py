"""
Slim a cached companyfacts payload into a test fixture (only the tags the engine reads, 2014+).
Usage: .venv/bin/python tests/fixtures/make_fixture.py AAPL KO JNJ
"""
import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from valuation_engine.normalize.tags import CONCEPTS  # noqa: E402

WANTED = {(c.taxonomy, t) for c in CONCEPTS for t in c.tags}
db = sqlite3.connect(".cache/edgar.sqlite")
tickers = json.loads(db.execute("SELECT payload FROM cache WHERE key LIKE '%company_tickers.json'").fetchone()[0])
by_ticker = {r["ticker"]: r for r in tickers.values()}

for ticker in sys.argv[1:]:
    cik = int(by_ticker[ticker]["cik_str"])
    key = f"https://data.sec.gov/api/xbrl/companyfacts/CIK{cik:010d}.json"
    raw = json.loads(db.execute("SELECT payload FROM cache WHERE key = ?", (key,)).fetchone()[0])
    slim = {"cik": raw["cik"], "entityName": raw["entityName"], "facts": {}}
    for taxonomy, tags in raw["facts"].items():
        for tag, body in tags.items():
            if (taxonomy, tag) not in WANTED:
                continue
            units = {u: [f for f in rows if f["end"] >= "2014-01-01"] for u, rows in body["units"].items()}
            units = {u: rows for u, rows in units.items() if rows}
            if units:
                slim["facts"].setdefault(taxonomy, {})[tag] = {"units": units}
    out = Path(__file__).parent / f"companyfacts_{ticker}.json"
    out.write_text(json.dumps(slim, separators=(",", ":")))
    print(ticker, cik, out.stat().st_size, "bytes")

# Minimal ticker list fixture
Path(__file__).parent / "company_tickers.json"
mini = {str(i): by_ticker[t] for i, t in enumerate(sys.argv[1:])}
(Path(__file__).parent / "company_tickers.json").write_text(json.dumps(mini))
