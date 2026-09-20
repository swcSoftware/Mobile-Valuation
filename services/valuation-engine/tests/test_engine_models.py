"""Model A / Model B against recorded fixtures."""
from valuation_engine.valuation.model_a import run_model_a
from valuation_engine.valuation.model_b import run_model_b
from valuation_engine.valuation.types import Assumptions


def assumptions() -> Assumptions:
    return Assumptions(aaa_yield_pct=5.0, treasury_10y_pct=4.2, hurdle_rate_pct=10.0, equity_risk_premium_pct=5.0,
                       beta=1.0, terminal_growth_pct=2.5, exit_multiple=15.0, tax_rate_pct=21.0)


def metric(result: dict, key: str) -> dict:
    return next(m for m in result["metrics"] if m["key"] == key)


def test_model_a_is_deterministic_and_transparent(aapl):
    a = run_model_a(aapl, assumptions(), price=300.0)
    b = run_model_a(aapl, assumptions(), price=300.0)
    assert a == b
    g = metric(a, "graham_g")
    assert g["value"] == 15.0  # clamped
    classic = metric(a, "graham_classic")
    eps = classic["inputs"]["eps_ttm"]
    assert abs(classic["value"] - eps * (8.5 + 2 * 15.0)) < 1e-9
    assert classic["sources"][0]["tag"] == "us-gaap:EarningsPerShareDiluted"
    revised = metric(a, "graham_revised")
    assert abs(revised["value"] - classic["value"] * 4.4 / 5.0) < 1e-9
    mos = a["margin_of_safety"]
    assert mos["market_price"] == 300.0
    assert mos["verdict"] in {"deep_value", "within_margin", "below_intrinsic_thin_margin", "above_intrinsic"}
    assert a["intrinsic_value_per_share"] == min(revised["value"], metric(a, "oe_value_hurdle")["value"])


def test_model_b_wacc_and_dcf(ko):
    r = run_model_b(ko, assumptions(), price=60.0)
    wacc = metric(r, "wacc")["value"]
    assert 5.0 < wacc < 12.0
    ke = metric(r, "cost_of_equity")["value"]
    assert abs(ke - (4.2 + 1.0 * 5.0)) < 1e-9
    for key in ("dcf_perpetuity", "dcf_exit_multiple", "roic", "eva", "moat_persistence"):
        assert metric(r, key)["value"] is not None, key
    assert r["intrinsic_value_per_share"] > 0
    assert metric(r, "moat_persistence")["value"] <= 10


def test_price_none_gives_insufficient_data(jnj):
    r = run_model_a(jnj, assumptions(), price=None)
    assert r["margin_of_safety"]["verdict"] == "insufficient_data"
    assert r["intrinsic_value_per_share"] is not None
