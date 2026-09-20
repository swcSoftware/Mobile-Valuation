"""Normalization rules against recorded companyfacts (no network)."""
from valuation_engine.normalize.statements import fiscal_year_for, growth_summary
from datetime import date


def test_ten_annual_periods_with_distinct_years(aapl, ko, jnj):
    for fin in (aapl, ko, jnj):
        years = [p.fiscal_year for p in fin.annual]
        assert len(years) == 10
        assert len(set(years)) == 10, years
        assert years == sorted(years)


def test_fiscal_year_for_52_53_week_years():
    assert fiscal_year_for(date(2021, 1, 3)) == 2020   # JNJ FY2020 ended Jan 3, 2021
    assert fiscal_year_for(date(2017, 12, 31)) == 2017
    assert fiscal_year_for(date(2025, 9, 27)) == 2025


def test_aapl_split_adjusted_eps(aapl):
    eps = {p.fiscal_year: p.get("eps_diluted") for p in aapl.annual}
    # Pre-2020 (4:1 split) EPS must be rescaled into the same regime as later years.
    assert eps[2016] < 3.0 and eps[2025] > 7.0
    assert any("split" in w.lower() for w in aapl.warnings)


def test_ttm_present_and_sourced(aapl):
    ttm = aapl.ttm
    assert ttm is not None
    for key in ("revenue", "net_income", "eps_diluted", "cfo", "capex", "equity", "cash"):
        assert key in ttm.values, key
        sv = ttm.values[key]
        assert sv.accession and sv.form and sv.period_end
    assert ttm.values["revenue"].derived is True  # FY + YTD − prior YTD


def test_derived_items(ko):
    latest = ko.latest_annual
    for key in ("fcf", "nwc", "owner_earnings", "nopat", "fcff", "invested_capital", "roic", "total_debt"):
        assert key in latest.values, key
        assert latest.values[key].derived
    assert latest.values["fcf"].value == latest.get("cfo") - abs(latest.get("capex"))


def test_jnj_ebit_fallback(jnj):
    latest = jnj.latest_annual
    assert "operating_income" in latest.values
    assert "EBIT proxy" in latest.values["operating_income"].note


def test_current_shares_reasonable(aapl, ko, jnj):
    for fin, lo, hi in ((aapl, 10e9, 20e9), (ko, 3e9, 5e9), (jnj, 2e9, 3e9)):
        assert fin.current_shares is not None
        assert lo < fin.current_shares.value < hi, (fin.ticker, fin.current_shares.value)


def test_growth_summary(aapl):
    g = growth_summary(aapl)
    assert g["revenue"]["full_period_years"] == 9
    assert 0 < g["revenue"]["full_period_cagr"] < 0.2
