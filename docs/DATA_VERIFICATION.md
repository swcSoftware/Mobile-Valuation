# Data Verification

Every number ValueLens shows passes through the verification gate in
`packages/valuation-core/.../DataChecks.kt` **before** a fair value is displayed. The rule behind
it: **no silent defaults**. Every model input is either read from a filing, measured from market
data, or explicitly labeled *assumed* — and an assumed input is always visible as a warning.

## Provenance of every model input

| Input | Source | Provenance label | Where it can go wrong | How to reconcile |
|---|---|---|---|---|
| Filer identity | SEC ticker list → CIK; predecessor via submissions + entity search **only when the successor filed an 8-K12B/8-K12G3** and the candidate shares the SIC with a recent 10-K predating the successor | `filer: sec` / `predecessor` | Holding-company reorganizations (XOM 2026). IPOs, spin-offs, foreign filers and funds are never substituted — they get a classified "no data" reason instead | Warning line names both CIKs; verify on EDGAR |
| Revenue, net income, EPS, cash flow, balance sheet | SEC EDGAR `companyfacts` (10-K / 10-Q XBRL) | `sec` | Tag switches between years (fixed Sprint 2: freshest tag wins), 52/53-week years, restatements | Expert Mode → tap the metric → tag, accession, period. Open the filing on EDGAR by accession. |
| TTM figures | FY(10-K) + YTD(10-Q) − prior YTD | `sec` (derived, note shows the arithmetic) | Misaligned periods when a tag lags | Check "TTM figures come from the same period" |
| Shares outstanding | `dei:EntityCommonStockSharesOutstanding` (summed across classes), else diluted weighted average | `sec` / `derived` | Multi-class filers (BRK, GOOG) report per class → count stale or partial | "Share count is current and consistent" compares cover-page vs diluted average |
| Market price | Yahoo chart quote (unofficial, keyless) or manual entry | `market` / `manual` / `none` | Stale quote, delisted ticker | Price date shown next to the price; override by tapping it |
| **Beta** | 5-year monthly regression of the stock's returns on the S&P 500 (`^GSPC`), adjusted closes, ≥ 36 months | `measured` (window, months, R² shown) / `override` / `assumed` | < 36 months of history (recent IPO) → assumed 1.0 with amber flag | Yahoo Finance "Beta (5Y Monthly)" uses the same convention; small differences come from the end month and dividend adjustment |
| AAA yield, 10-yr Treasury | FRED `DAAA` / `DGS10` via `rates.json` on GitHub Pages (published by CI; key never in the app) | `fred` / `override` / `assumed` | Publish job stalls → stale file | "as of" date shown; > 7 days → warning; Settings → Refresh rates |
| Effective tax rate | income tax ÷ pre-tax income from the latest filing, clamped 0–50% | `sec` / `assumed` (21%) | Negative pre-tax income | Check "Tax rate from filings" |
| Cost of debt | interest expense ÷ total debt, clamped 2–12% | `sec` / `assumed` (rf + 1.5%) | Interest not tagged (AAPL) | Check "Cost of debt from filings" |
| Change in working capital (owner earnings, FCFF) | Normalized: average NWC ÷ revenue over 5 fiscal years × this period's Δrevenue (TTM annualized); raw one-year change kept alongside | `sec` (derived, note lists the 5 ratios) | One-off acquisition payments, tax timing, big receivables (KO 2025, JNJ talc) | `working_capital` check warns when raw and normalized differ by > 30% of net income |
| Growth (g, stage-1) | CAGR of EPS / revenue / FCF from the 10-K history, clamped 0–15% | `sec` | Split-adjusted EPS history; < 2 years of data | Expert Mode → Growth (CAGR) card |
| Sector (bank / insurer / broker / REIT / mREIT) | SIC code from the SEC submissions profile; mortgage REIT by D&A ÷ revenue < 5% | `sector` | Conglomerates with a financial SIC (BRK); stale SIC after a pivot | `sector_mode` check names the mode; expert mode shows the SIC |
| Cost of equity | CAPM = rf + β × ERP, **floored at rf + 4%** | shown on the metric with the unfloored CAPM value | Very low measured beta (KO 0.29, PGR 0.23) would imply 6% | Note on the metric says when the floor applied |
| Hurdle rate, ERP, terminal growth, exit multiple, MoS bands | Policy constants (10%, 5%, 2.5%, 15×, 25/50%) | shown in Assumptions, user-editable | — | These are the model's *opinions*, not data; change them in Settings |

## The checks

| Key | Pass | Warn | Fail |
|---|---|---|---|
| `share_classes` | classes reconciled with EPS-based ratios | a conversion ratio assumed 1:1 | — |
| `sector_mode` | SIC known; models fit the industry (general / financial / REIT) | SIC unknown — general models used | — |
| `filer_identity` | ticker's CIK has 10-K history | filings taken from a predecessor filer (holding-company reorg) | — |
| `balance_sheet` | assets = liabilities + equity within 0.5% | within 5% | > 5% |
| `eps_consistency` | reported EPS within 10% of NI ÷ diluted shares | within 25% | further |
| `share_count` | cover-page shares within 0.7–1.3× diluted average | 0.4–2.5× | outside, or no count |
| `ttm_alignment` | revenue / NI / CFO end within 10 days | within 100 days | further |
| `filing_freshness` | latest period ≤ 130 days old | ≤ 400 days | older |
| `price` | quote ≤ 5 days old or manual | older or missing | — |
| `signs` | revenue, shares, D&A positive | — | any violated |
| `working_capital` | raw one-year ΔNWC within 30% of net income of the normalized figure | further apart (one-off) | — |
| `tag_coverage` | every line item from a current tag | stale tags dropped (lists them) | — |
| `debt_coverage` | debt tags found | liabilities > 0 but no debt tag | — |
| `beta` | measured | assumed | — |
| `tax_rate` | derived | assumed | — |
| `cost_of_debt` | derived | assumed | — |
| `rates` | FRED ≤ 7 days old or user override | stale or defaults | — |

**Fail** hides the fair value ("Value withheld") in both apps. **Warn** shows beside it.
Basic mode shows one summary line; Expert Mode (or tapping the card) shows every check.

## Reference implementation

The Python engine (`services/valuation-engine`) is the oracle for normalization and the two
models; `packages/valuation-core` must reproduce it within 1e-6 on the recorded fixtures
(`OracleTest`). The checks, beta and rates loader exist only in the core (they need the device's
network path). Regenerate the oracle after any normalization change:

```bash
cd services/valuation-engine && .venv/bin/python tests/fixtures/make_expected.py
```

## Known gaps (Sprint 3)

- Multi-class share structures still lack per-class facts (ISSUES #1); the share-count check flags them.
- Banks / insurers have no operating income or classified balance sheet; several checks warn rather than adapt.
- Beta uses a public quote feed; a licensed source would remove the "unofficial" caveat.

## Sector models (Sprint 3)

| Sector | Model A | Model B | Hidden as not applicable |
|---|---|---|---|
| Banks, insurers, brokers (SIC 6020–6299, 6311–6411, 6712) and mortgage REITs | Graham EPS formulas; book value × justified P/B = min((ROE − g)/(Ke − g), 4×); composite = min | Residual income: B0 + PV of (ROE − Ke)·B over 5 yrs + terminal (spread capped at 15 pts); book grows at ROE × (1 − payout) | Owner earnings, NNWC, FCFF, WACC, ROIC |
| Property REITs (6798) | Graham formula on FFO/share (FFO = NI + D&A − gains on sale); dividend coverage | mean(FFO × multiple, DPS × (1+g)/(Ke − g)) | NNWC, owner earnings, FCFF DCF |
| Everything else | unchanged general models | unchanged | — |
