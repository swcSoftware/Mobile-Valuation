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


def test_working_capital_normalization_dampens_one_offs(ko):
    """KO FY2025 absorbed ~$10B of working capital (fairlife payment); owner earnings must not collapse."""
    fy25 = next(p for p in ko.annual if p.fiscal_year == 2025)
    assert "delta_nwc_normalized" in fy25.values
    raw, norm = fy25.get("delta_nwc"), fy25.get("delta_nwc_normalized")
    assert raw > 5e9 and abs(norm) < 2e9
    assert "avg(NWC/revenue over 5 yrs" in fy25.values["delta_nwc_normalized"].note
    oe = [p.get("owner_earnings") for p in ko.annual[-3:]]
    assert max(oe) / min(oe) < 1.6, oe   # no 5× whipsaw


def test_ttm_normalization_is_annualized(aapl):
    sv = aapl.ttm.values["delta_nwc_normalized"]
    assert "annualized" in sv.note or aapl.ttm.form == "10-K"


def test_capex_other_variant_is_last_resort_per_period():
    """ISSUES #71: LLY/VZ file capex only under the "Other" tags. They fill a year that has no main
    capex tag, and never override one that does (for most filers the "Other" line is a subset)."""
    import json
    from pathlib import Path
    from valuation_engine.edgar.companyfacts import parse_companyfacts
    from valuation_engine.edgar.tickers import CompanyRef
    from valuation_engine.normalize.statements import normalize

    raw = json.loads((Path(__file__).parent / "fixtures" / "companyfacts_CAPEX_OTHER.json").read_text())
    fin = normalize(parse_companyfacts(raw, CompanyRef(ticker="CAPX", cik=1, name=raw["entityName"])))
    capex = {p.fiscal_year: p.values["capex"] for p in fin.annual}
    assert capex[2024].value == 80e6 and capex[2024].tag == "PaymentsToAcquirePropertyPlantAndEquipment"
    assert capex[2025].value == 90e6 and capex[2025].tag == "PaymentsToAcquireOtherPropertyPlantAndEquipment"


def test_debt_tagged_as_notes_payable_is_read():
    """ISSUES #92: Realty Income tags no LongTermDebt*; its $25B of notes are NotesPayable. Before, total
    debt was $1.4B of commercial paper and O graded A on debt load."""
    from conftest import load_fin
    o = load_fin("O")
    assert o.ttm.values["long_term_debt"].tag == "NotesPayable"
    assert abs(o.ttm.get("total_debt") - (25_092e6 + 1_400e6)) < 50e6
    assert 0.6 < o.ttm.get("debt_to_equity") < 0.75


def test_filers_with_long_term_debt_tags_keep_them(aapl, ko, jnj):
    """The new debt tags sit last: a filer with a LongTermDebt* tag never reaches them."""
    for fin in (aapl, ko, jnj):
        for p in fin.annual + [fin.ttm]:
            if "long_term_debt" in p.values:
                assert p.values["long_term_debt"].tag.startswith("LongTermDebt"), (fin.ticker, p.label, p.values["long_term_debt"].tag)


def _ttm_edges():
    import json
    from pathlib import Path
    from valuation_engine.edgar.companyfacts import parse_companyfacts
    from valuation_engine.edgar.tickers import CompanyRef
    from valuation_engine.normalize.statements import normalize
    raw = json.loads((Path(__file__).parent / "fixtures" / "companyfacts_TTM_EDGES.json").read_text())
    return normalize(parse_companyfacts(raw, CompanyRef(ticker="TTME", cik=2, name=raw["entityName"]))).ttm


def test_a_filed_twelve_month_column_is_the_ttm():
    """ISSUES #98 (Amazon): the 10-Q's twelve-months-ended figure is used as filed, not rebuilt or dropped."""
    t = _ttm_edges()
    ni = t.values["net_income"]
    assert ni.value == 136e6 and ni.period_end.isoformat() == "2026-06-30" and "as filed in the 10-Q" in ni.note
    assert t.values["revenue"].value == 1300e6   # no twelve-month column: FY + H1 − prior H1, as before


def test_computed_annual_values_are_rebuilt_not_carried(jnj):
    """ISSUES #97: JNJ's operating income is the EBIT proxy; in the TTM it must run to the latest quarter."""
    t = jnj.ttm
    assert t.values["operating_income"].taxonomy == "valuelens"
    assert t.values["operating_income"].period_end == t.values["revenue"].period_end


def test_lagging_lines_are_rebuilt_or_labeled():
    """ISSUES #99, owner policy: rebuild where the inputs are current, otherwise keep the annual figure and say so."""
    t = _ttm_edges()
    pretax = t.values["pretax_income"]
    assert pretax.taxonomy == "valuelens" and pretax.value == 136e6 + 34e6
    assert "isn't reported in the latest 10-Q" in pretax.note
    da = t.values["d_and_a"]
    assert da.value == 60e6 and da.period_end.isoformat() == "2025-12-31"
    assert da.note.startswith("From the 10-K for the period ending 2025-12-31")
    assert "From the" not in t.values["revenue"].note, "current lines are untouched"
