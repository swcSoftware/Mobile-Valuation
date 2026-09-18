# ValueLens — Development Plan

Source of truth for scope and sequencing. Task-level tracking lives in [TASKS.md](TASKS.md);
defects in [ISSUES.md](ISSUES.md); what shipped when in [SPRINTS.md](SPRINTS.md).

## Product in one paragraph

Native iOS + Android app that values a US-listed company the way Graham, Buffett and Munger
would — from its own SEC 10-K / 10-Q filings, with every number traceable to an XBRL line item —
and lets the user flip to a modern DCF / ROIC lens for contrast. No technical analysis, ever.

## Decisions taken in Sprint 0 (2026-09-18)

| Topic | Decision | Why |
|---|---|---|
| Data source | Live SEC EDGAR via local Python engine; bundled AAPL/KO/MSFT JSON as offline fallback | Real numbers from day one; app stays tappable without the server |
| Platforms | iOS built out; Android scaffolded only | Budget depth on one client, keep contract parity |
| Prices | Yahoo chart endpoint (unofficial), Stooq fallback, manual override in-app | SEC has no prices; keyless providers for alpha |
| Rates | FRED `DAAA` / `DGS10` when `FRED_API_KEY` set, else editable defaults | Graham's revised formula and CAPM need live yields |
| iOS target | iOS 17 | `@Observable`, Swift Charts, NavigationStack |
| Repo layout | `apps/ios`, `apps/android`, `services/valuation-engine`, `docs` | Room for shared packages later |
| Maintenance capex | `min(capex, D&A)` | Conservative, transparent proxy; noted on every OE metric |
| Graham g | EPS CAGR over available 10-K history, clamped 0–15% | Graham warned against extrapolating high growth |
| Model A composite | `min(Graham revised, OE @ hurdle)` | Lower of the two classic estimates |
| Model B composite | `mean(DCF perpetuity, DCF exit multiple)` | Two terminal-value conventions averaged |
| Tests | Only formula unit tests; no UI tests in alpha | Owner instruction: speed over coverage until beta |

## Roadmap

### Sprint 0 — Alpha skeleton ✅ (this session)
Engine (EDGAR → normalize → Model A/B), iOS app (onboarding, case study, watchlist, search,
company detail, settings, PDF + share card), Android scaffold, docs, three-branch git flow.

### Sprint 1 — Any ticker, anywhere
Goal: the app works on a physical iPhone for any ticker without a Mac nearby.
See [PATH_TO_ANY_TICKER.md](PATH_TO_ANY_TICKER.md). Also: Android screens ported to parity.

### Sprint 2 — Data quality & model depth
Multi-class share handling (BRK, GOOG), dimensioned XBRL facts, ΔNWC smoothing, 10-Q
trajectory view (revenue/margin/CFO accelerating or decelerating vs last 10-K), sector-aware
tag fallbacks (banks, insurers, REITs), user-adjustable maintenance-capex method.

### Sprint 3 — Polish & beta
TestFlight / Play internal track, crash reporting, analytics-free telemetry of engine errors,
onboarding copy pass, accessibility audit (Dynamic Type, VoiceOver on the MoS gauge),
localization scaffolding, App Store assets.

### Later
Filing-level drill-down (open the 10-K section behind a number), notes per company,
comparison view (two tickers side by side), Apple Watch complication for watchlist MoS.

## Non-goals (permanent)
Chart patterns, moving averages, RSI/MACD/oscillators, volume analysis, momentum screens,
price alerts based on technical levels, anything that treats price history as signal.
