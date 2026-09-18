# Valuation Engine API (v0.1)

Base URL (dev): `http://127.0.0.1:8000`. Interactive docs: `/docs`.

All endpoints accept `X-SEC-User-Agent: <Full Name> <email>`; the engine forwards it to SEC as
`User-Agent`. Without it the engine's `SEC_USER_AGENT` default is used (dev only).

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | `{status, version}` |
| GET | `/search?q=&limit=` | Ticker/name search over SEC's master list → `{results: [CompanyRef]}` |
| GET | `/market/rates` | `{aaa_yield_pct, treasury_10y_pct, source, as_of}` |
| GET | `/companies/{ticker}/financials` | Normalized annual + TTM statements with sources |
| GET | `/companies/{ticker}/valuation` | Full report (below). Query overrides: `price`, `aaa_yield_pct`, `treasury_10y_pct`, `hurdle_rate_pct`, `equity_risk_premium_pct`, `beta`, `terminal_growth_pct`, `exit_multiple`, `tax_rate_pct` |

Errors: `404 {"detail": "Unknown ticker: X"}`; `5xx` on upstream failure.

## ValuationReport

```jsonc
{
  "company": {"ticker": "AAPL", "cik": 320193, "name": "Apple Inc."},
  "quote": {"ticker", "price", "currency", "as_of", "source"} | null,
  "assumptions": {"aaa_yield_pct", "treasury_10y_pct", "hurdle_rate_pct", "equity_risk_premium_pct",
                  "beta", "terminal_growth_pct", "exit_multiple", "tax_rate_pct", "projection_years",
                  "max_growth_pct", "mos_bands_pct": [25, 50], "rate_source"},
  "snapshot": { "<concept>": SourcedValue },           // TTM key lines + ratios
  "history": [ {"fiscal_year", "period_end", "revenue", "net_income", "eps_diluted", "fcf",
                "owner_earnings", "equity", "roic", "book_value_per_share", "cfo", "capex"} ],
  "growth": { "<concept>": {"full_period_years", "full_period_cagr", "five_year_cagr"} },
  "model_a": ModelResult, "model_b": ModelResult,
  "warnings": ["..."], "disclaimer": "...", "generated_at": "ISO-8601"
}

SourcedValue = {"value", "tag": "us-gaap:NetIncomeLoss", "accession", "form", "period_end",
                "period_start", "filed", "derived": bool, "note"}

Metric = {"key", "label", "value": number|null, "unit": "USD"|"USD/share"|"%"|"x"|"shares",
          "formula", "inputs": {name: number|null}, "sources": [SourcedValue], "notes": [str]}

ModelResult = {"name", "intrinsic_value_per_share", "composite": Metric, "metrics": [Metric],
               "margin_of_safety": {"intrinsic_value", "market_price", "margin_of_safety_pct",
                                    "bands": [{"discount_pct", "buy_below"}], "formula",
                                    "verdict": "deep_value"|"within_margin"|"below_intrinsic_thin_margin"|"above_intrinsic"|"insufficient_data"}}
```

### Model A metric keys
`graham_g`, `graham_classic`, `graham_revised`, `nnwc`, `owner_earnings`, `owner_earnings_per_share`,
`oe_value_hurdle`, `oe_value_treasury`

### Model B metric keys
`cost_of_equity`, `cost_of_debt`, `wacc`, `fcff_growth`, `dcf_perpetuity`, `dcf_exit_multiple`,
`fcff_projection` (inputs only), `roic`, `roic_wacc_spread`, `eva`, `moat_persistence`

Contract changes must update: this file, `apps/ios/.../ValuationReport.swift`,
`apps/android/.../Models.kt`, and the bundled `SampleData/*.json`.
