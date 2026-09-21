"""
Model B — Modern Fundamental Fair Value.

  WACC  = E/(D+E)·Ke + D/(D+E)·Kd·(1−t)
  Ke    = rf + β·ERP                         (CAPM; rf = 10-yr Treasury)
  Kd    = interest_expense / total_debt      (clamped 2–12%)
  FCFF  = NOPAT + D&A − CapEx − ΔNWC
  DCF   = Σ FCFF_t/(1+WACC)^t + TV/(1+WACC)^N
  TV    = FCFF_N·(1+g)/(WACC−g)   [perpetuity]   or   FCFF_N × exit multiple
  Equity value = EV − total debt + cash ; per share = / shares
  ROIC  = NOPAT / invested capital ; EVA = (ROIC − WACC) × invested capital
"""
from __future__ import annotations

from ..normalize.statements import NormalizedFinancials, growth_summary
from .model_a import margin_of_safety
from .types import Assumptions, Metric


def cost_of_capital(fin: NormalizedFinancials, a: Assumptions, price: float | None) -> list[Metric]:
    ttm = fin.ttm
    v = ttm.values if ttm else {}
    rf = a.treasury_10y_pct / 100.0
    ke = rf + a.beta * a.equity_risk_premium_pct / 100.0
    debt = v["total_debt"].value if "total_debt" in v else 0.0
    interest = abs(v["interest_expense"].value) if "interest_expense" in v else None
    kd_raw = (interest / debt) if (interest is not None and debt > 0) else None
    kd = min(max(kd_raw, 0.02), 0.12) if kd_raw is not None else rf + 0.015
    t = v["effective_tax_rate"].value if "effective_tax_rate" in v else a.tax_rate_pct / 100.0
    shares = fin.current_shares.value if fin.current_shares else None
    mcap = price * shares if (price and shares) else None
    if mcap is None:
        # fall back to book equity weights
        mcap = v["equity"].value if "equity" in v else None
    notes = []
    if mcap is None or mcap <= 0:
        we, wd = 1.0, 0.0
        notes.append("No market cap or book equity available; assuming 100% equity.")
    else:
        we, wd = mcap / (mcap + debt), debt / (mcap + debt)
    wacc = we * ke + wd * kd * (1 - t)
    return [
        Metric("cost_of_equity", "Cost of equity (CAPM)", ke * 100, "%", "Ke = rf + β × ERP",
               inputs={"rf_pct": a.treasury_10y_pct, "beta": a.beta, "erp_pct": a.equity_risk_premium_pct}),
        Metric("cost_of_debt", "Cost of debt (pre-tax)", kd * 100, "%", "Kd = interest_expense / total_debt (clamped 2–12%)",
               inputs={"interest_expense": interest, "total_debt": debt, "raw_pct": kd_raw * 100 if kd_raw is not None else None},
               sources=[v[k] for k in ("interest_expense", "total_debt") if k in v]),
        Metric("wacc", "WACC", wacc * 100, "%", "WACC = E/(D+E)·Ke + D/(D+E)·Kd·(1−t)",
               inputs={"equity_weight": we, "debt_weight": wd, "ke_pct": ke * 100, "kd_pct": kd * 100, "tax_rate": t,
                       "market_cap": mcap, "total_debt": debt},
               notes=notes),
    ]


def fcff_growth(fin: NormalizedFinancials, a: Assumptions) -> Metric:
    g = growth_summary(fin)
    picks = []
    for key in ("fcf", "revenue", "net_income"):
        c = g.get(key, {}).get("five_year_cagr")
        if c is not None:
            picks.append((key, c))
    if not picks:
        return Metric("fcff_growth", "Stage-1 growth", 5.0, "%", "default", notes=["No growth history; using 5% default."])
    # Conservative: use the median of the available 5-yr CAGRs, clamped.
    vals = sorted(c for _, c in picks)
    median = vals[len(vals) // 2]
    clamped = max(0.0, min(median * 100.0, a.max_growth_pct))
    return Metric("fcff_growth", f"Stage-1 growth ({a.projection_years} yrs)", clamped, "%",
                  f"median(5-yr CAGR of {', '.join(k for k, _ in picks)}), clamped to [0, {a.max_growth_pct:.0f}%]",
                  inputs={k: c * 100 for k, c in picks})


def dcf(fin: NormalizedFinancials, a: Assumptions, wacc_pct: float, g1_pct: float) -> list[Metric]:
    ttm = fin.ttm
    if ttm is None or "fcff" not in ttm.values:
        return [Metric("dcf_perpetuity", "DCF fair value (perpetuity)", None, "USD/share", "see module docstring",
                       notes=["FCFF unavailable."])]
    v = ttm.values
    fcff0 = v["fcff"].value
    w, g1, gt = wacc_pct / 100.0, g1_pct / 100.0, a.terminal_growth_pct / 100.0
    n = a.projection_years
    projected = [fcff0 * (1 + g1) ** t for t in range(1, n + 1)]
    pv_stage1 = sum(f / (1 + w) ** t for t, f in enumerate(projected, start=1))
    notes = []
    if w <= gt:
        notes.append("WACC ≤ terminal growth; perpetuity model undefined. Using exit multiple only.")
        tv_perp = None
    else:
        tv_perp = projected[-1] * (1 + gt) / (w - gt)
    tv_exit = projected[-1] * a.exit_multiple
    debt = v["total_debt"].value if "total_debt" in v else 0.0
    cash = (v["cash"].value if "cash" in v else 0.0) + (v["short_term_investments"].value if "short_term_investments" in v else 0.0)
    shares = fin.current_shares.value if fin.current_shares else None

    def equity_per_share(tv: float | None) -> float | None:
        if tv is None or not shares:
            return None
        ev = pv_stage1 + tv / (1 + w) ** n
        v = (ev - debt + cash) / shares
        return v if v > 0 else None  # negative projected cash flow: a DCF is not meaningful, not a negative price

    if fcff0 <= 0:
        notes.append(f"Free cash flow to the firm is negative ({fcff0:,.0f}); a DCF cannot value a business that consumes cash — see owner earnings and the growth history instead.")

    base_inputs = {"fcff_ttm": fcff0, "wacc_pct": wacc_pct, "stage1_growth_pct": g1_pct, "years": n,
                   "pv_stage1": pv_stage1, "total_debt": debt, "cash_and_sti": cash, "shares": shares}
    return [
        Metric("dcf_perpetuity", "DCF fair value (perpetuity growth)", equity_per_share(tv_perp), "USD/share",
               "Σ FCFF_t/(1+WACC)^t + [FCFF_N(1+g)/(WACC−g)]/(1+WACC)^N − debt + cash, ÷ shares",
               inputs={**base_inputs, "terminal_growth_pct": a.terminal_growth_pct, "terminal_value": tv_perp},
               sources=[v["fcff"]], notes=notes),
        Metric("dcf_exit_multiple", "DCF fair value (exit multiple)", equity_per_share(tv_exit), "USD/share",
               "Σ FCFF_t/(1+WACC)^t + [FCFF_N × multiple]/(1+WACC)^N − debt + cash, ÷ shares",
               inputs={**base_inputs, "exit_multiple": a.exit_multiple, "terminal_value": tv_exit},
               sources=[v["fcff"]]),
        Metric("fcff_projection", "Projected FCFF", None, "USD", "FCFF_t = FCFF_0 × (1+g)^t",
               inputs={f"year_{t}": f for t, f in enumerate(projected, start=1)}),
    ]


def roic_eva(fin: NormalizedFinancials, wacc_pct: float) -> list[Metric]:
    ttm = fin.ttm
    v = ttm.values if ttm else {}
    if "roic" not in v or "invested_capital" not in v:
        return [Metric("roic", "ROIC (TTM)", None, "%", "nopat / invested_capital", notes=["Missing inputs."])]
    roic = v["roic"].value
    ic = v["invested_capital"].value
    w = wacc_pct / 100.0
    eva = (roic - w) * ic
    history = [(p.fiscal_year, p.get("roic")) for p in fin.annual if p.get("roic") is not None]
    years_above = sum(1 for _, r in history if r is not None and r > w)
    return [
        Metric("roic", "ROIC (TTM)", roic * 100, "%", "ROIC = NOPAT / (equity + total_debt − cash)",
               inputs={"nopat": v["nopat"].value, "invested_capital": ic}, sources=[v["operating_income"], v["equity"]]),
        Metric("roic_wacc_spread", "ROIC − WACC spread", (roic - w) * 100, "%", "ROIC − WACC",
               inputs={"roic_pct": roic * 100, "wacc_pct": wacc_pct},
               notes=["Positive spread = value creation; the wider and more persistent, the stronger the moat."]),
        Metric("eva", "Economic value added (TTM)", eva, "USD", "EVA = (ROIC − WACC) × invested_capital",
               inputs={"roic_pct": roic * 100, "wacc_pct": wacc_pct, "invested_capital": ic}),
        Metric("moat_persistence", "Years ROIC > WACC", float(years_above), "x",
               f"count(years with ROIC > current WACC) over {len(history)} yrs of 10-K history",
               inputs={f"FY{fy}": r * 100 for fy, r in history if r is not None}),
    ]


def run_model_b(fin: NormalizedFinancials, a: Assumptions, price: float | None) -> dict:
    coc = cost_of_capital(fin, a, price)
    wacc = next(m for m in coc if m.key == "wacc").value or a.hurdle_rate_pct
    g1 = fcff_growth(fin, a)
    dcf_metrics = dcf(fin, a, wacc, g1.value or 0.0)
    roic = roic_eva(fin, wacc)
    vals = [m.value for m in dcf_metrics if m.key in ("dcf_perpetuity", "dcf_exit_multiple") and m.value is not None]
    fair = sum(vals) / len(vals) if vals else None
    composite = Metric("model_b_fair_value", "Model B fair value", fair, "USD/share",
                       "mean(dcf_perpetuity, dcf_exit_multiple)", inputs={m.key: m.value for m in dcf_metrics[:2]})
    return {
        "name": "Modern Fundamental Fair Value (DCF / ROIC)",
        "intrinsic_value_per_share": fair,
        "composite": composite.to_dict(),
        "metrics": [m.to_dict() for m in [*coc, g1, *dcf_metrics, *roic]],
        "margin_of_safety": margin_of_safety(fair, price, a),
    }
