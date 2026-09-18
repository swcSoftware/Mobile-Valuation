"""
Build normalized annual (10-K) and trailing-twelve-month (10-K + 10-Q) statements
from raw companyfacts.

Rules (kept deliberately simple and auditable):
  * Annual FLOW values: 10-K facts whose duration is ~1 year, keyed by period end.
    When a value is restated in a later filing, the most recently filed value wins.
  * Annual INSTANT values: 10-K facts whose end date equals a fiscal-year end.
  * TTM FLOW values:  FY(latest 10-K) + YTD(latest 10-Q) − YTD(same period, prior year).
  * TTM INSTANT values: most recent balance-sheet date from any filing.
Every value records the XBRL tag, accession number, form and period it came from.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from ..edgar.companyfacts import CompanyFacts, Fact
from .tags import CONCEPTS, CONCEPTS_BY_KEY, Concept, Kind

ANNUAL_FORMS = {"10-K", "10-K/A", "10-KT", "10-K405"}
QUARTERLY_FORMS = {"10-Q", "10-Q/A", "10-QT"}
MIN_ANNUAL_DAYS, MAX_ANNUAL_DAYS = 340, 380


@dataclass
class SourcedValue:
    value: float
    tag: str
    taxonomy: str
    accession: str
    form: str
    period_end: date
    period_start: date | None
    filed: date
    derived: bool = False
    note: str = ""

    def to_dict(self) -> dict:
        return {
            "value": self.value,
            "tag": f"{self.taxonomy}:{self.tag}",
            "accession": self.accession,
            "form": self.form,
            "period_end": self.period_end.isoformat(),
            "period_start": self.period_start.isoformat() if self.period_start else None,
            "filed": self.filed.isoformat(),
            "derived": self.derived,
            "note": self.note,
        }


@dataclass
class Period:
    """One fiscal period (annual or TTM) with all resolved concept values."""
    label: str
    period_end: date
    fiscal_year: int | None
    form: str
    values: dict[str, SourcedValue] = field(default_factory=dict)

    def get(self, key: str) -> float | None:
        v = self.values.get(key)
        return v.value if v else None

    def to_dict(self) -> dict:
        return {
            "label": self.label,
            "period_end": self.period_end.isoformat(),
            "fiscal_year": self.fiscal_year,
            "form": self.form,
            "values": {k: v.to_dict() for k, v in self.values.items()},
        }


@dataclass
class NormalizedFinancials:
    ticker: str
    cik: int
    name: str
    annual: list[Period]          # oldest -> newest
    ttm: Period | None
    current_shares: SourcedValue | None
    warnings: list[str] = field(default_factory=list)

    @property
    def latest_annual(self) -> Period | None:
        return self.annual[-1] if self.annual else None


# --------------------------------------------------------------------------- helpers
def _latest_filed(facts: list[Fact]) -> Fact:
    return max(facts, key=lambda f: (f.filed, f.end))


def _to_sv(f: Fact, **kw) -> SourcedValue:
    return SourcedValue(
        value=f.value, tag=f.tag, taxonomy=f.taxonomy, accession=f.accession,
        form=f.form, period_end=f.end, period_start=f.start, filed=f.filed, **kw,
    )


def _near(a: date, b: date, days: int) -> bool:
    return abs((a - b).days) <= days


def _concept_facts(cf: CompanyFacts, concept: Concept) -> list[tuple[str, list[Fact]]]:
    """All (tag, facts) pairs for a concept, restricted to the expected unit."""
    out = []
    for tag in concept.tags:
        facts = [f for f in cf.get(concept.taxonomy, tag) if f.unit == concept.unit]
        if facts:
            out.append((tag, facts))
    return out


# --------------------------------------------------------------------------- annual
def _annual_flow(cf: CompanyFacts, concept: Concept) -> dict[date, SourcedValue]:
    """period_end -> value, using the first tag that has annual data for that period."""
    result: dict[date, SourcedValue] = {}
    for tag, facts in _concept_facts(cf, concept):
        by_end: dict[date, list[Fact]] = {}
        for f in facts:
            if f.form not in ANNUAL_FORMS or f.start is None:
                continue
            d = f.duration_days or 0
            if MIN_ANNUAL_DAYS <= d <= MAX_ANNUAL_DAYS:
                by_end.setdefault(f.end, []).append(f)
        for end, group in by_end.items():
            if end not in result:  # earlier tag in the priority list wins
                result[end] = _to_sv(_latest_filed(group))
    return result


def _annual_instant(cf: CompanyFacts, concept: Concept, ends: list[date]) -> dict[date, SourcedValue]:
    result: dict[date, SourcedValue] = {}
    for tag, facts in _concept_facts(cf, concept):
        by_end: dict[date, list[Fact]] = {}
        for f in facts:
            if f.form in ANNUAL_FORMS and f.start is None:
                by_end.setdefault(f.end, []).append(f)
        for end in ends:
            if end in result:
                continue
            if end in by_end:
                result[end] = _to_sv(_latest_filed(by_end[end]))
    return result


# --------------------------------------------------------------------------- TTM
def _ttm_flow(cf: CompanyFacts, concept: Concept, fy_value: SourcedValue | None) -> SourcedValue | None:
    """FY + YTD(current) − YTD(prior year). Falls back to FY when no newer 10-Q exists."""
    for tag, facts in _concept_facts(cf, concept):
        q = [f for f in facts if f.form in QUARTERLY_FORMS and f.start is not None]
        if not q:
            continue
        latest_end = max(f.end for f in q)
        if fy_value is not None and latest_end <= fy_value.period_end:
            return fy_value  # the 10-K is the freshest data we have
        # YTD fact = the one with the longest duration ending on latest_end
        cur_candidates = [f for f in q if f.end == latest_end]
        cur = max(cur_candidates, key=lambda f: ((f.duration_days or 0), f.filed))
        if concept.unit == "shares":
            return _to_sv(cur, note="Weighted-average shares from latest 10-Q YTD period")
        assert cur.start is not None
        # Prior-year comparable YTD (same filing usually includes it)
        prior_start, prior_end = cur.start - timedelta(days=365), cur.end - timedelta(days=365)
        prior = [
            f for f in facts
            if f.start is not None and _near(f.start, prior_start, 10) and _near(f.end, prior_end, 10)
        ]
        # Annual anchor must end right before cur.start
        if fy_value is None or not _near(fy_value.period_end, cur.start - timedelta(days=1), 8):
            # Try to find any annual fact for this tag that ends right before cur.start
            annual = [
                f for f in facts if f.form in ANNUAL_FORMS and f.start is not None
                and MIN_ANNUAL_DAYS <= (f.duration_days or 0) <= MAX_ANNUAL_DAYS
                and _near(f.end, cur.start - timedelta(days=1), 8)
            ]
            if not annual:
                continue
            fy_value = _to_sv(_latest_filed(annual))
        if not prior:
            continue
        p = _latest_filed(prior)
        ttm = fy_value.value + cur.value - p.value
        note = (
            f"TTM = FY({fy_value.period_end}) {fy_value.value:,.0f} + YTD({cur.start}→{cur.end}) "
            f"{cur.value:,.0f} − YTD({p.start}→{p.end}) {p.value:,.0f}"
        )
        if concept.unit == "USD/shares":
            note += " (EPS TTM is an additive approximation)"
        return SourcedValue(
            value=ttm, tag=tag, taxonomy=concept.taxonomy, accession=cur.accession,
            form=cur.form, period_end=cur.end, period_start=cur.end - timedelta(days=365),
            filed=cur.filed, derived=True, note=note,
        )
    return fy_value


def _ttm_instant(cf: CompanyFacts, concept: Concept) -> SourcedValue | None:
    for tag, facts in _concept_facts(cf, concept):
        inst = [f for f in facts if f.start is None and f.form in (ANNUAL_FORMS | QUARTERLY_FORMS)]
        if inst:
            latest_end = max(f.end for f in inst)
            return _to_sv(_latest_filed([f for f in inst if f.end == latest_end]))
    return None


# --------------------------------------------------------------------------- derived
def _derive(period: Period, prev: Period | None) -> None:
    """Add computed line items that the models need. All flagged derived=True."""
    v = period.values

    def mk(value: float, note: str, base: SourcedValue) -> SourcedValue:
        return SourcedValue(
            value=value, tag="derived", taxonomy="valuelens", accession=base.accession,
            form=base.form, period_end=base.period_end, period_start=base.period_start,
            filed=base.filed, derived=True, note=note,
        )

    # total_liabilities fallback
    if "total_liabilities" not in v and "liabilities_and_equity" in v and "equity" in v:
        v["total_liabilities"] = mk(
            v["liabilities_and_equity"].value - v["equity"].value,
            "LiabilitiesAndStockholdersEquity − StockholdersEquity", v["equity"],
        )
    # total debt
    debt = 0.0
    parts = []
    for k in ("short_term_debt", "long_term_debt"):
        if k in v:
            debt += v[k].value
            parts.append(k)
    if parts:
        v["total_debt"] = mk(debt, " + ".join(parts), v[parts[0]])
    # free cash flow
    if "cfo" in v and "capex" in v:
        v["fcf"] = mk(v["cfo"].value - abs(v["capex"].value), "cfo − capex", v["cfo"])
    # non-cash working capital = (current assets − cash − STI) − (current liabilities − short-term debt)
    if "current_assets" in v and "current_liabilities" in v:
        ca = v["current_assets"].value - v.get("cash", mk(0, "", v["current_assets"])).value
        ca -= v["short_term_investments"].value if "short_term_investments" in v else 0.0
        cl = v["current_liabilities"].value
        cl -= v["short_term_debt"].value if "short_term_debt" in v else 0.0
        v["nwc"] = mk(ca - cl, "(current_assets − cash − short_term_investments) − (current_liabilities − short_term_debt)", v["current_assets"])
        if prev is not None and "nwc" in prev.values:
            v["delta_nwc"] = mk(v["nwc"].value - prev.values["nwc"].value, f"nwc − nwc({prev.period_end})", v["nwc"])
    # effective tax rate
    if "income_tax" in v and "pretax_income" in v and v["pretax_income"].value > 0:
        rate = v["income_tax"].value / v["pretax_income"].value
        v["effective_tax_rate"] = mk(max(0.0, min(rate, 0.5)), "income_tax / pretax_income (clamped 0–50%)", v["income_tax"])
    # maintenance capex proxy
    if "capex" in v and "d_and_a" in v:
        v["maintenance_capex"] = mk(
            min(abs(v["capex"].value), abs(v["d_and_a"].value)),
            "min(capex, d_and_a) — conservative proxy; Buffett's 'average capex to maintain competitive position'",
            v["capex"],
        )
    # owner earnings
    if all(k in v for k in ("net_income", "d_and_a", "maintenance_capex")):
        dwc = v["delta_nwc"].value if "delta_nwc" in v else 0.0
        oe = v["net_income"].value + v["d_and_a"].value - v["maintenance_capex"].value - dwc
        v["owner_earnings"] = mk(oe, "net_income + d_and_a − maintenance_capex − delta_nwc", v["net_income"])
    # NOPAT & FCFF
    if "operating_income" in v:
        t = v["effective_tax_rate"].value if "effective_tax_rate" in v else 0.21
        v["nopat"] = mk(v["operating_income"].value * (1 - t), f"operating_income × (1 − {t:.3f})", v["operating_income"])
        if "d_and_a" in v and "capex" in v:
            dwc = v["delta_nwc"].value if "delta_nwc" in v else 0.0
            v["fcff"] = mk(v["nopat"].value + v["d_and_a"].value - abs(v["capex"].value) - dwc,
                           "nopat + d_and_a − capex − delta_nwc", v["nopat"])
    # invested capital = equity + total_debt − cash
    if "equity" in v:
        ic = v["equity"].value + (v["total_debt"].value if "total_debt" in v else 0.0) - (v["cash"].value if "cash" in v else 0.0)
        v["invested_capital"] = mk(ic, "equity + total_debt − cash", v["equity"])
        if "nopat" in v and ic > 0:
            v["roic"] = mk(v["nopat"].value / ic, "nopat / invested_capital", v["nopat"])
    if "equity" in v and "shares_diluted" in v and v["shares_diluted"].value > 0:
        v["book_value_per_share"] = mk(v["equity"].value / v["shares_diluted"].value, "equity / shares_diluted", v["equity"])
    if "net_income" in v and "equity" in v and v["equity"].value > 0:
        v["roe"] = mk(v["net_income"].value / v["equity"].value, "net_income / equity", v["net_income"])
    if "current_assets" in v and "current_liabilities" in v and v["current_liabilities"].value > 0:
        v["current_ratio"] = mk(v["current_assets"].value / v["current_liabilities"].value, "current_assets / current_liabilities", v["current_assets"])
    if "total_debt" in v and "equity" in v and v["equity"].value > 0:
        v["debt_to_equity"] = mk(v["total_debt"].value / v["equity"].value, "total_debt / equity", v["total_debt"])
    if "operating_income" in v and "revenue" in v and v["revenue"].value > 0:
        v["operating_margin"] = mk(v["operating_income"].value / v["revenue"].value, "operating_income / revenue", v["operating_income"])
    if "net_income" in v and "revenue" in v and v["revenue"].value > 0:
        v["net_margin"] = mk(v["net_income"].value / v["revenue"].value, "net_income / revenue", v["net_income"])


# --------------------------------------------------------------------------- splits
PER_SHARE_KEYS = ("eps_diluted",)
SHARE_COUNT_KEYS = ("shares_diluted",)


def _adjust_for_splits(periods: list[Period], warnings: list[str]) -> None:
    """
    companyfacts reports per-share figures as originally filed. A 10-K only restates the
    two prior years, so older EPS values straddle stock splits unadjusted. Detect a split
    from a discontinuity in diluted share count and scale everything before it.
    """
    for i in range(1, len(periods)):
        cur, prev = periods[i].get("shares_diluted"), periods[i - 1].get("shares_diluted")
        if not cur or not prev or prev <= 0:
            continue
        ratio = cur / prev
        if ratio >= 1.8 or ratio <= 0.55:
            factor = round(ratio * 2) / 2  # snap to common split ratios (2, 3, 4, 7, 1.5, 0.5 …)
            if factor <= 0:
                continue
            warnings.append(f"Share-count discontinuity ({factor:g}×) between FY{periods[i-1].fiscal_year} and FY{periods[i].fiscal_year} (stock split, as restated in later filings); earlier per-share data adjusted.")
            for p in periods[:i]:
                for k in PER_SHARE_KEYS:
                    if k in p.values:
                        sv = p.values[k]
                        sv.value = sv.value / factor
                        sv.derived = True
                        sv.note = (sv.note + " " if sv.note else "") + f"split-adjusted ÷{factor:g}"
                for k in SHARE_COUNT_KEYS:
                    if k in p.values:
                        sv = p.values[k]
                        sv.value = sv.value * factor
                        sv.derived = True
                        sv.note = (sv.note + " " if sv.note else "") + f"split-adjusted ×{factor:g}"


# --------------------------------------------------------------------------- entry point
def normalize(cf: CompanyFacts, max_years: int = 10) -> NormalizedFinancials:
    warnings: list[str] = []
    flows = [c for c in CONCEPTS if c.kind == Kind.FLOW]
    instants = [c for c in CONCEPTS if c.kind == Kind.INSTANT and c.taxonomy == "us-gaap"]

    annual_flow_values = {c.key: _annual_flow(cf, c) for c in flows}
    # Fiscal period ends = every end that has a revenue or net income figure
    ends = sorted(set(annual_flow_values["revenue"]) | set(annual_flow_values["net_income"]))
    ends = ends[-max_years:]
    if not ends:
        warnings.append("No annual 10-K income statement data found.")

    annual_instant_values = {c.key: _annual_instant(cf, c, ends) for c in instants}

    periods: list[Period] = []
    for end in ends:
        p = Period(label=f"FY{end.year}", period_end=end, fiscal_year=end.year, form="10-K")
        for key, series in annual_flow_values.items():
            if end in series:
                p.values[key] = series[end]
        for key, series in annual_instant_values.items():
            if end in series:
                p.values[key] = series[end]
        periods.append(p)
    _adjust_for_splits(periods, warnings)
    for i, p in enumerate(periods):
        _derive(p, periods[i - 1] if i > 0 else None)

    # TTM
    ttm: Period | None = None
    latest = periods[-1] if periods else None
    if latest is not None:
        ttm = Period(label="TTM", period_end=latest.period_end, fiscal_year=None, form="10-K+10-Q")
        for c in flows:
            sv = _ttm_flow(cf, c, latest.values.get(c.key))
            if sv:
                ttm.values[c.key] = sv
                ttm.period_end = max(ttm.period_end, sv.period_end)
        for c in instants:
            sv = _ttm_instant(cf, c)
            if sv:
                ttm.values[c.key] = sv
        # Drop anything stale (a tag the company stopped using years ago must not leak into TTM).
        stale = [k for k, sv in ttm.values.items() if (ttm.period_end - sv.period_end).days > 540]
        for k in stale:
            del ttm.values[k]
        if stale:
            warnings.append("Dropped stale TTM values (tag no longer reported): " + ", ".join(stale))
        # Δ working capital for TTM is measured against the prior fiscal year end
        _derive(ttm, periods[-2] if len(periods) > 1 else None)
        if ttm.period_end == latest.period_end:
            ttm.form = "10-K"

    shares_concept = CONCEPTS_BY_KEY["shares_outstanding"]
    current_shares: SourcedValue | None = None
    for tag, facts in _concept_facts(cf, shares_concept):
        latest_end = max(f.end for f in facts)
        candidates = [f for f in facts if f.end == latest_end]
        # Multi-class filers report one undimensioned fact per class at the same date; sum them.
        total = sum(f.value for f in candidates)
        current_shares = _to_sv(_latest_filed(candidates))
        if len(candidates) > 1:
            current_shares.value = total
            current_shares.derived = True
            current_shares.note = f"Sum of {len(candidates)} share classes reported on {latest_end}"
        break
    anchor = ttm.period_end if ttm else (latest.period_end if latest else None)
    if current_shares is not None and anchor is not None and (anchor - current_shares.period_end).days > 540:
        warnings.append(
            f"dei:EntityCommonStockSharesOutstanding is stale ({current_shares.period_end}); "
            "likely a multi-class filer whose share data is dimensioned by class. Ignoring it."
        )
        current_shares = None
    if current_shares is None and ttm and "shares_diluted" in ttm.values:
        current_shares = ttm.values["shares_diluted"]
        warnings.append("Using diluted weighted-average shares as current share count.")
    if current_shares is None:
        warnings.append("No usable share count found; per-share values unavailable.")

    missing = [c.key for c in CONCEPTS if latest and c.key not in latest.values and c.key != "shares_outstanding"]
    if missing:
        warnings.append("Latest 10-K missing concepts: " + ", ".join(missing))

    return NormalizedFinancials(
        ticker=cf.ref.ticker, cik=cf.ref.cik, name=cf.entity_name,
        annual=periods, ttm=ttm, current_shares=current_shares, warnings=warnings,
    )


def cagr(first: float | None, last: float | None, years: int) -> float | None:
    if first is None or last is None or years <= 0 or first <= 0 or last <= 0:
        return None
    return (last / first) ** (1.0 / years) - 1.0


def growth_summary(fin: NormalizedFinancials) -> dict[str, dict]:
    """CAGR of key series over the full available window and the last 5 years."""
    out: dict[str, dict] = {}
    if len(fin.annual) < 2:
        return out
    for key in ("revenue", "net_income", "eps_diluted", "equity", "fcf", "owner_earnings", "book_value_per_share"):
        series = [(p.fiscal_year, p.get(key)) for p in fin.annual if p.get(key) is not None]
        if len(series) < 2:
            continue
        (fy0, v0), (fy1, v1) = series[0], series[-1]
        entry = {"full_period_years": fy1 - fy0, "full_period_cagr": cagr(v0, v1, fy1 - fy0)}
        five = [s for s in series if s[0] >= fy1 - 5]
        if len(five) >= 2:
            (fa, va), (fb, vb) = five[0], five[-1]
            entry["five_year_cagr"] = cagr(va, vb, fb - fa)
        out[key] = entry
    return out
