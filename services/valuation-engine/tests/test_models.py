"""Deterministic unit checks on the valuation formulas (no network)."""
from datetime import date

from valuation_engine.normalize.statements import NormalizedFinancials, Period, SourcedValue, cagr
from valuation_engine.valuation.model_a import graham_values, margin_of_safety
from valuation_engine.valuation.types import Assumptions, Metric


def sv(value: float, end=date(2025, 12, 31)) -> SourcedValue:
    return SourcedValue(value=value, tag="t", taxonomy="us-gaap", accession="a", form="10-K",
                        period_end=end, period_start=None, filed=end)


def assumptions(**kw) -> Assumptions:
    base = dict(aaa_yield_pct=4.4, treasury_10y_pct=4.0, hurdle_rate_pct=10.0, equity_risk_premium_pct=5.0,
                beta=1.0, terminal_growth_pct=2.5, exit_multiple=15.0, tax_rate_pct=21.0)
    base.update(kw)
    return Assumptions(**base)


def test_graham_classic_and_revised():
    ttm = Period("TTM", date(2025, 12, 31), None, "10-K", {"eps_diluted": sv(5.0)})
    fin = NormalizedFinancials("T", 1, "Test", [], ttm, sv(100.0))
    g = Metric("graham_g", "g", 10.0, "%", "")
    classic, revised = graham_values(fin, assumptions(), g)
    assert classic.value == 5.0 * (8.5 + 20)  # 142.5
    assert revised.value == classic.value  # Y == 4.4 -> identical


def test_margin_of_safety_bands():
    mos = margin_of_safety(100.0, 60.0, assumptions())
    assert mos["margin_of_safety_pct"] == 40.0
    assert mos["bands"][0]["buy_below"] == 75.0
    assert mos["verdict"] == "within_margin"
    assert margin_of_safety(100.0, 120.0, assumptions())["verdict"] == "above_intrinsic"


def test_cagr():
    assert abs(cagr(100, 200, 10) - 0.0718) < 1e-3
    assert cagr(-1, 5, 3) is None
