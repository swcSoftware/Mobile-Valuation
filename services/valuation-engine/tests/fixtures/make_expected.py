"""
Freeze the Python engine's output on the recorded fixtures as the oracle for the Kotlin core.
Deterministic: fixed assumptions, fixed price, no network.
Usage: .venv/bin/python tests/fixtures/make_expected.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from valuation_engine.edgar.companyfacts import parse_companyfacts  # noqa: E402
from valuation_engine.edgar.tickers import CompanyRef  # noqa: E402
from valuation_engine.normalize.statements import growth_summary, normalize  # noqa: E402
from valuation_engine.valuation.model_a import run_model_a  # noqa: E402
from valuation_engine.valuation.model_b import run_model_b  # noqa: E402
from valuation_engine.valuation.types import Assumptions  # noqa: E402

FIX = Path(__file__).parent
PRICES = {"AAPL": 300.0, "KO": 60.0, "JNJ": 150.0}
ASSUMPTIONS = Assumptions(aaa_yield_pct=5.94, treasury_10y_pct=4.94, hurdle_rate_pct=10.0, equity_risk_premium_pct=5.0,
                          beta=1.0, terminal_growth_pct=2.5, exit_multiple=15.0, tax_rate_pct=21.0, rate_source="fixture")

for ticker, price in PRICES.items():
    raw = json.loads((FIX / f"companyfacts_{ticker}.json").read_text())
    ref = CompanyRef(ticker=ticker, cik=raw["cik"], name=raw["entityName"])
    fin = normalize(parse_companyfacts(raw, ref))
    out = {
        "company": {"ticker": ticker, "cik": raw["cik"], "name": raw["entityName"]},
        "price": price,
        "assumptions": ASSUMPTIONS.to_dict(),
        "annual": [p.to_dict() for p in fin.annual],
        "ttm": fin.ttm.to_dict() if fin.ttm else None,
        "current_shares": fin.current_shares.to_dict() if fin.current_shares else None,
        "growth": growth_summary(fin),
        "warnings": fin.warnings,
        "model_a": run_model_a(fin, ASSUMPTIONS, price),
        "model_b": run_model_b(fin, ASSUMPTIONS, price),
    }
    (FIX / f"expected_{ticker}.json").write_text(json.dumps(out, indent=1, sort_keys=True))
    print(ticker, "annual periods", len(fin.annual), "A", out["model_a"]["intrinsic_value_per_share"], "B", out["model_b"]["intrinsic_value_per_share"])
