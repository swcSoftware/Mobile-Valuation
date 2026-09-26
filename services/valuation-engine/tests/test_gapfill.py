"""ISSUES #53: companyfacts lagging a filing is filled from that filing's XBRL (no network here)."""
import asyncio
import json
from datetime import date
from pathlib import Path

from conftest import load_raw
from valuation_engine.edgar import gapfill
from valuation_engine.edgar.companyfacts import parse_companyfacts
from valuation_engine.edgar.tickers import CompanyRef
from valuation_engine.normalize.statements import normalize

FIX = Path(__file__).parent / "fixtures"
KO_Q2 = {"accession": "0001628280-26-050503", "primary": "ko-20260703.htm", "form": "10-Q",
         "filed": date(2026, 7, 29), "period": "2026-07-03"}


def _cf(ticker):
    raw = load_raw(ticker)
    return parse_companyfacts(raw, CompanyRef(ticker=ticker, cik=raw["cik"], name=raw["entityName"]))


def _submissions(f):
    return {"filings": {"recent": {"form": ["8-K", f["form"]], "accessionNumber": ["x", f["accession"]],
                                   "primaryDocument": ["x.htm", f["primary"]],
                                   "filingDate": ["2026-09-01", f["filed"].isoformat()], "reportDate": ["", f["period"]]}}}


class _Client:
    """Serves submissions; fails the test if the instance is fetched when it shouldn't be."""
    def __init__(self, filing, allow_instance):
        self.filing, self.allow = filing, allow_instance

    async def get_json(self, url):
        return _submissions(self.filing)

    async def get_text(self, url):
        assert self.allow, f"fetched {url} for a filer that isn't lagging"
        assert url.endswith("/000162828026050503/ko-20260703_htm.xml"), url
        return (FIX / "instance_KO_2026Q2.xml").read_text()


def test_latest_filing_skips_8ks():
    assert gapfill.latest_filing(_submissions(KO_Q2))["accession"] == KO_Q2["accession"]


def test_filers_that_are_not_lagging_are_returned_untouched():
    cf = _cf("AAPL")
    older = dict(KO_Q2, filed=gapfill.latest_filed(cf))            # same day as companyfacts' newest: not lagging
    out, note = asyncio.run(gapfill.fill(_Client(older, allow_instance=False), cf))
    assert out is cf and note is None


def test_ko_june_quarter_is_read_from_the_filing():
    cf = _cf("KO")
    assert gapfill.latest_filed(cf) == date(2026, 4, 30)
    out, note = asyncio.run(gapfill.fill(_Client(KO_Q2, allow_instance=True), cf))
    assert note == ("SEC's companyfacts feed has not yet published the 10-Q filed 2026-07-29, period ending 2026-07-03; "
                    "108 figures were read directly from that filing's XBRL.")
    fin, before = normalize(out), normalize(cf)
    assert before.ttm.period_end == date(2026, 4, 3)
    assert fin.ttm.period_end == date(2026, 7, 3)
    rev = fin.ttm.values["revenue"]
    assert rev.accession == KO_Q2["accession"]
    assert rev.value == 47_941e6 + 25_852e6 - 23_664e6          # FY2025 + H1 2026 − H1 2025, all as filed
    # Annual periods come from 10-Ks, which the quarter doesn't touch.
    assert [p.to_dict() for p in fin.annual] == [p.to_dict() for p in before.annual]


def test_dimensioned_facts_are_skipped():
    facts = gapfill.parse_instance((FIX / "instance_KO_2026Q2.xml").read_text(), KO_Q2)
    revenues = [f for f in facts if f.tag == "Revenues"]
    assert 2_426e6 not in [f.value for f in revenues]              # one segment's revenue (context c-37)
    assert all(f.form == "10-Q" and f.filed == KO_Q2["filed"] for f in facts)
