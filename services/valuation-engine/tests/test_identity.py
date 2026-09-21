from datetime import date

from valuation_engine.edgar.identity import FilerProfile, is_plausible_predecessor, parse_profile, search_prefixes, search_token


def prof(**kw):
    base = dict(cik=1, name="X", sic="2911", first_filing=date(2026, 7, 1), latest_10k=date(2026, 2, 18), has_successor_notice=False)
    base.update(kw)
    return FilerProfile(**base)


def test_search_token_and_prefixes():
    assert search_token("ExxonMobil Holdings Corp") == "exxonmobil"
    assert search_token("The Coca-Cola Co") == "coca-cola"
    assert search_token("Holdings Inc") is None
    assert search_prefixes("exxonmobil") == ["exxonmobil", "exxon", "exxo"]


def test_plausibility_rules():
    today = date(2026, 9, 21)
    succ = prof(cik=2115436, name="ExxonMobil Holdings Corp", latest_10k=None)
    assert is_plausible_predecessor(succ, prof(cik=34088), today)
    assert not is_plausible_predecessor(succ, prof(cik=2, sic="2080"), today)               # different industry
    assert not is_plausible_predecessor(succ, prof(cik=3, latest_10k=date(2023, 2, 1)), today)  # stale
    assert not is_plausible_predecessor(succ, prof(cik=4, latest_10k=date(2026, 9, 1)), today)  # after the successor appeared
    assert not is_plausible_predecessor(succ, prof(cik=5, latest_10k=None), today)


def test_parse_profile():
    p = parse_profile({"cik": "999", "name": "Newco", "sic": "3571", "filings": {"recent": {"form": ["8-K12B", "10-Q"], "filingDate": ["2026-07-01", "2026-08-01"]}}})
    assert p.has_successor_notice and p.first_filing == date(2026, 7, 1) and p.latest_10k is None
