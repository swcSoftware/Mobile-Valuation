"""
Model A — Traditional Value Analysis (Graham / Buffett / Munger).

  Graham classic:   V* = EPS × (8.5 + 2g)
  Graham revised:   V* = EPS × (8.5 + 2g) × 4.4 / Y          (Y = AAA corporate yield, %)
  NNWC:             Cash & STI + 0.75·Receivables + 0.50·Inventory − Total Liabilities
  Owner earnings:   NI + D&A − Maintenance CapEx − ΔWorking Capital
  OE value:         OE / r   (zero-growth perpetuity; r = hurdle or 10-yr Treasury)
  Margin of safety: bands at 25% and 50% below intrinsic value
"""
from __future__ import annotations

from ..normalize.statements import NormalizedFinancials, growth_summary
from .types import Assumptions, Metric


def _pct(x: float | None) -> float | None:
    return None if x is None else x * 100.0


def graham_growth_rate(fin: NormalizedFinancials, a: Assumptions) -> Metric:
    """g for the Graham formula: 7–10 yr EPS CAGR, clamped to [0, max_growth]."""
    g = growth_summary(fin).get("eps_diluted", {})
    raw = g.get("full_period_cagr")
    years = g.get("full_period_years")
    notes = []
    if raw is None:
        # fall back to net income growth (share count changes distort EPS)
        ni = growth_summary(fin).get("net_income", {})
        raw, years = ni.get("full_period_cagr"), ni.get("full_period_years")
        notes.append("EPS CAGR unavailable; using net income CAGR.")
    if raw is None:
        return Metric("graham_g", "Expected growth (g)", None, "%",
                      "CAGR(EPS diluted, first→last 10-K)", notes=notes + ["Insufficient history."])
    clamped = max(0.0, min(raw * 100.0, a.max_growth_pct))
    if clamped != raw * 100.0:
        notes.append(f"Raw CAGR {raw*100:.1f}% clamped to [0, {a.max_growth_pct:.0f}%] per Graham's caution on extrapolation.")
    eps_series = [p.values["eps_diluted"] for p in fin.annual if "eps_diluted" in p.values]
    return Metric(
        "graham_g", "Expected growth (g)", clamped, "%",
        f"CAGR(EPS) over {years} yrs = (EPS_last / EPS_first)^(1/{years}) − 1, clamped to [0, {a.max_growth_pct:.0f}%]",
        inputs={"raw_cagr_pct": _pct(raw), "years": years},
        sources=[eps_series[0], eps_series[-1]] if eps_series else [],
        notes=notes,
    )


def graham_values(fin: NormalizedFinancials, a: Assumptions, g: Metric) -> list[Metric]:
    ttm = fin.ttm
    if ttm is None or "eps_diluted" not in ttm.values or g.value is None:
        return [Metric("graham_classic", "Graham intrinsic value (classic)", None, "USD/share",
                       "EPS × (8.5 + 2g)", notes=["Missing EPS or growth."])]
    eps = ttm.values["eps_diluted"]
    gv = g.value
    classic = eps.value * (8.5 + 2 * gv)
    revised = classic * 4.4 / a.aaa_yield_pct if a.aaa_yield_pct > 0 else None
    return [
        Metric("graham_classic", "Graham intrinsic value (classic)", classic, "USD/share",
               "V* = EPS × (8.5 + 2g)",
               inputs={"eps_ttm": eps.value, "g_pct": gv}, sources=[eps],
               notes=["8.5 = P/E of a no-growth company per Graham (1962)."]),
        Metric("graham_revised", "Graham intrinsic value (revised 1974)", revised, "USD/share",
               "V* = EPS × (8.5 + 2g) × 4.4 / Y",
               inputs={"eps_ttm": eps.value, "g_pct": gv, "aaa_yield_pct": a.aaa_yield_pct}, sources=[eps],
               notes=["4.4 = average AAA yield when Graham published; Y = current AAA yield."]),
    ]


def nnwc(fin: NormalizedFinancials) -> Metric:
    ttm = fin.ttm
    formula = "NNWC = cash + short_term_investments + 0.75×receivables + 0.50×inventory − total_liabilities"
    if ttm is None or "total_liabilities" not in ttm.values:
        return Metric("nnwc", "Net-net working capital", None, "USD", formula, notes=["Missing balance sheet."])
    v = ttm.values
    cash = v["cash"].value if "cash" in v else 0.0
    sti = v["short_term_investments"].value if "short_term_investments" in v else 0.0
    ar = v["receivables"].value if "receivables" in v else 0.0
    inv = v["inventory"].value if "inventory" in v else 0.0
    tl = v["total_liabilities"].value
    total = cash + sti + 0.75 * ar + 0.50 * inv - tl
    shares = fin.current_shares.value if fin.current_shares else None
    per_share = total / shares if shares else None
    srcs = [v[k] for k in ("cash", "short_term_investments", "receivables", "inventory", "total_liabilities") if k in v]
    m = Metric("nnwc", "Net-net working capital", total, "USD", formula,
               inputs={"cash": cash, "short_term_investments": sti, "receivables": ar, "inventory": inv,
                       "total_liabilities": tl, "shares_outstanding": shares},
               sources=srcs,
               notes=["Negative NNWC is normal for most operating businesses; a positive NNWC per share above price is Graham's classic 'net-net' bargain."])
    m.notes.append(f"Per share: {per_share:,.2f}" if per_share is not None else "Per-share value unavailable.")
    m.inputs["nnwc_per_share"] = per_share
    return m


def owner_earnings(fin: NormalizedFinancials, a: Assumptions) -> list[Metric]:
    ttm = fin.ttm
    formula = "OE = net_income + d_and_a − maintenance_capex − delta_nwc_normalized"
    if ttm is None or "owner_earnings" not in ttm.values:
        return [Metric("owner_earnings", "Owner earnings (TTM)", None, "USD", formula, notes=["Missing inputs."])]
    v = ttm.values
    oe = v["owner_earnings"]
    inputs = {
        "net_income": v["net_income"].value,
        "d_and_a": v["d_and_a"].value,
        "maintenance_capex": v["maintenance_capex"].value,
        "delta_nwc_normalized": v["delta_nwc_normalized"].value if "delta_nwc_normalized" in v else (v["delta_nwc"].value if "delta_nwc" in v else 0.0),
        "delta_nwc_raw": v["delta_nwc"].value if "delta_nwc" in v else None,
    }
    srcs = [v["net_income"], v["d_and_a"], v["capex"]]
    notes = [v["maintenance_capex"].note]
    if "delta_nwc_normalized" in v:
        notes.append("Working capital: " + v["delta_nwc_normalized"].note)
    metrics = [Metric("owner_earnings", "Owner earnings (TTM)", oe.value, "USD", formula, inputs=inputs, sources=srcs, notes=notes)]
    shares = fin.current_shares.value if fin.current_shares else None
    if shares:
        oe_ps = oe.value / shares
        metrics.append(Metric("owner_earnings_per_share", "Owner earnings / share", oe_ps, "USD/share",
                              "owner_earnings / shares_outstanding", inputs={"owner_earnings": oe.value, "shares": shares},
                              sources=[fin.current_shares] if fin.current_shares else []))
        for key, label, r in (
            ("oe_value_hurdle", f"OE value @ {a.hurdle_rate_pct:.1f}% hurdle", a.hurdle_rate_pct),
            ("oe_value_treasury", f"OE value @ {a.treasury_10y_pct:.2f}% 10-yr Treasury", a.treasury_10y_pct),
        ):
            val = oe_ps / (r / 100.0) if r > 0 else None
            metrics.append(Metric(key, label, val, "USD/share", "V = OE_per_share / r   (zero terminal growth)",
                                  inputs={"owner_earnings_per_share": oe_ps, "r_pct": r},
                                  notes=["Buffett: discount owner earnings at the long bond rate with no growth inflation; the conservatism is the point."]))
    return metrics


def margin_of_safety(intrinsic: float | None, price: float | None, a: Assumptions) -> dict:
    bands = [
        {"discount_pct": b, "buy_below": (intrinsic * (1 - b / 100.0)) if intrinsic is not None else None}
        for b in a.mos_bands_pct
    ]
    mos_pct = None
    if intrinsic and price and intrinsic > 0:
        mos_pct = (1 - price / intrinsic) * 100.0
    return {
        "intrinsic_value": intrinsic,
        "market_price": price,
        "margin_of_safety_pct": mos_pct,
        "bands": bands,
        "formula": "MoS = 1 − price / intrinsic_value",
        "verdict": _verdict(mos_pct, a),
    }


def _verdict(mos_pct: float | None, a: Assumptions) -> str:
    if mos_pct is None:
        return "insufficient_data"
    hi, lo = max(a.mos_bands_pct), min(a.mos_bands_pct)
    if mos_pct >= hi:
        return "deep_value"
    if mos_pct >= lo:
        return "within_margin"
    if mos_pct >= 0:
        return "below_intrinsic_thin_margin"
    return "above_intrinsic"


def run_model_a(fin: NormalizedFinancials, a: Assumptions, price: float | None) -> dict:
    g = graham_growth_rate(fin, a)
    graham = graham_values(fin, a, g)
    oe = owner_earnings(fin, a)
    nn = nnwc(fin)

    # Composite intrinsic value = conservative blend: min of Graham revised and OE @ hurdle when both exist.
    candidates = {m.key: m.value for m in graham + oe if m.key in ("graham_revised", "oe_value_hurdle") and m.value}
    intrinsic = min(candidates.values()) if candidates else None
    composite = Metric(
        "model_a_intrinsic", "Model A intrinsic value", intrinsic, "USD/share",
        "min(graham_revised, oe_value_hurdle)", inputs=candidates,
        notes=["Conservative blend: the lower of the two classic estimates."],
    )
    return {
        "name": "Traditional Value (Graham / Buffett / Munger)",
        "intrinsic_value_per_share": intrinsic,
        "composite": composite.to_dict(),
        "metrics": [m.to_dict() for m in [g, *graham, nn, *oe]],
        "margin_of_safety": margin_of_safety(intrinsic, price, a),
    }
