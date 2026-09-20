# Valuation Report Contract (v0.2)

> Since Sprint 2 the report is produced **on-device** by `packages/valuation-core`; the HTTP
> endpoints below are the Python reference engine's and remain for development. The JSON shape is
> the contract shared by the core, iOS and Android.

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

### Errors (all non-2xx)

```jsonc
{"error": {"code": "unknown_ticker", "message": "…", "detail": {…}}, "detail": "…"}   // `detail` string kept for Sprint-0 clients
```

| code | HTTP | Meaning |
|---|---|---|
| `unknown_ticker` | 404 | Not in SEC's company list |
| `no_annual_data` | 422 | Filer exists but has no 10-K income statement facts (20-F, fund, SPAC, new listing) |
| `upstream_unavailable` | 503 | SEC / provider unreachable or 5xx |
| `rate_limited` | 429 | Per-identity limit hit; `Retry-After` header set |
| `invalid_identity` | 400 | `REQUIRE_IDENTITY=true` and header isn't `Full Name email` |
| `engine_error` | 500 | Unhandled failure (logged server-side) |

`/health` additionally returns `rates`, `price_providers`, `cache` stats, `require_identity`,
`client_rate_limit_per_minute` and `lan_addresses` (for physical devices on the same Wi-Fi).

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
  "warnings": ["..."], "disclaimer": "...", "generated_at": "ISO-8601",
  "data_checks": [ {"key", "label", "status": "pass"|"warn"|"fail", "message", "inputs": [str]} ],   // core only
  "provenance": { "beta": "measured"|"assumed"|"override", "rates": "fred"|"assumed"|"override",
                  "price": "market"|"manual"|"none", "tax_rate": "sec"|"assumed", "cost_of_debt": "sec"|"assumed",
                  "beta_detail": "β 0.38 · 59 monthly returns 2021-10→2026-09 · R² 0.10" }          // core only
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

Contract changes must update: this file, `packages/valuation-core/.../domain/Models.kt`,
`apps/ios/.../ValuationReport.swift`, the bundled `SampleData/*.json` (iOS) and `assets/*.json` (Android),
and the test fixtures under `services/valuation-engine/tests/fixtures`.
