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
| Data source | Live SEC EDGAR via local Python engine; bundled AAPL/KO/MSFT JSON as offline fallback | Real numbers from day one; app stays tappable without the server. **Superseded 2026-09-19: engine moves on-device (see below).** |
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
| Change style | Additive fixes behind a trigger or a guard; rewrites only for global defects or an agreed stronger model | Owner decision 2026-09-22: most filers already value correctly, so a fix for one filer must not reshape their path. Full rule in [CLAUDE.md](../CLAUDE.md) |

## Roadmap

### Sprint 0 — Alpha skeleton ✅ (this session)
Engine (EDGAR → normalize → Model A/B), iOS app (onboarding, case study, watchlist, search,
company detail, settings, PDF + share card), Android scaffold, docs, three-branch git flow.

### Sprint 1 — Any ticker, anywhere
Goal: the app works on a physical iPhone for any ticker without a Mac nearby.
See [PATH_TO_ANY_TICKER.md](PATH_TO_ANY_TICKER.md). Also: Android screens ported to parity.

### Architecture decision (2026-09-19): on-device, no hosted engine
The blueprint's hosted Python engine is retired as a production dependency. Every step it
performs — EDGAR fetch, XBRL normalization, Model A/B math, caching — runs on the user's phone.
Reasons: zero ops cost, each user's own IP/identity against SEC (what fair-access intends),
nothing leaves the device except requests to public data sources, and no single point of failure.
The Python engine stays as the **reference implementation and test oracle**: the shared mobile
core must reproduce its output on the recorded fixtures byte-for-byte (within float tolerance).

Secrets never ship in the app. Anything that needs a key (FRED) is turned into a **public static
file published by a scheduled GitHub Action to GitHub Pages**; the app reads the file.

### Sprint 2 — On-device engine, consumer clarity, verified data
Goal: a real phone runs the whole app with nothing on the owner's Mac; a non-expert can read a
valuation without a finance degree; no number reaches a formula without passing a data check.

**Track A — On-device core**
1. `packages/valuation-core` (Kotlin Multiplatform): port `normalize/` + `valuation/` from Python.
   Android consumes it as a Gradle module; iOS as an XCFramework. Pure Kotlin, no platform APIs.
2. EDGAR client in the core (ktor/OkHttp on Android, URLSession bridge on iOS) with the user's
   `User-Agent`, on-device SQLite/file cache, SEC 10 req/s courtesy limit per device.
3. Cross-implementation test: run AAPL/KO/JNJ fixtures through Python and the KMP core; diff JSON.
4. Remove engine URL / LAN settings from both apps; delete `RemoteValuationRepository`.
   The Python `main.py` stays runnable for development and doc generation only.

**Track B — Rates via GitHub Pages (keyless)**
5. `scripts/publish_rates.py` + `.github/workflows/rates.yml` (cron, weekdays 11:00 UTC): fetch
   FRED `DAAA`/`DGS10` with the repo secret, write `rates.json` (current + 30-day history) to the
   `gh-pages` branch. Enable Pages. URL: `https://swcsoftware.github.io/Mobile-Valuation/rates.json`.
6. Apps fetch on launch (12 h TTL), cache, show "Rates: FRED · as of <date>" or "(cached)";
   amber "Rates may be stale" when > 7 days; manual override in Settings wins.
7. Same mechanism for `tickers.json` (weekly SEC company list mirror) → instant, offline search.

**Track C — Consumer clarity: Expert Mode**
8. Settings toggle **Expert Mode** (off by default, persisted). Off = "basic" presentation:
   - Company screen shows: price, one fair-value number per model with a plain-language sentence
     ("Based on its filings, ValueLens estimates this business is worth about $74 per share; the
     market is asking $270."), the margin-of-safety bar, the verdict, and 4 headline health
     facts (growth, profitability, debt, cash). Formulas, XBRL tags, WACC/NNWC/EVA rows hidden.
   - Every number keeps a small ⓘ that opens a one-paragraph explanation written for a
     first-time investor (no formulas).
   - Case study copy rewritten at a general-audience reading level; expert copy kept as the
     "Show me the math" variant.
   On = today's full detail plus **Expand all** / **Collapse all** for metric disclosures.
9. Glossary screen (Settings → Glossary): intrinsic value, margin of safety, owner earnings,
   free cash flow, ROIC, WACC, beta, book value — each in ≤ 60 words, expert paragraph below.
10. Model names in basic mode: "Classic value (Graham/Buffett)" and "Cash-flow value (DCF)".

**Track D — Data verification gate (runs before any calculation)**
11. `DataCheck` layer in the core; each check yields pass / warn / fail with a plain message and
    is shown in a "Data checks" card (basic mode: one line "12 checks passed, 1 warning ⓘ").
    Initial checks:
    - Balance sheet balances: assets ≈ liabilities + equity (±0.5 %)
    - EPS ≈ net income ÷ diluted shares (±10 %) — catches split/tag mismatches
    - Share count sanity: market cap = price × shares within 0.1×–10× of book equity … or flag
    - Period alignment: TTM components come from consecutive quarters; no gaps > 100 days
    - Freshness: latest 10-Q/10-K ≤ 130 days old, price ≤ 5 days old
    - Non-negative where required (revenue, shares), sign sanity for capex/D&A
    - Every model input has a provenance (SEC tag, market feed, or *assumption*) — **no silent
      defaults**: an assumed value fails the check unless the user has acknowledged it
12. **Beta** (ISSUES #31): not an SEC datum, so it must be *derived*, not assumed. Compute 5-year
    monthly regression beta vs S&P 500 from price history (Yahoo chart, keyless) on-device;
    display method + R² + window so users can reconcile with Yahoo (which uses the same
    5y-monthly convention). Fallback when < 36 months of history: industry-neutral 1.0 shown as
    "assumed" with an amber flag, never as a bare number.
13. Effective tax rate, cost of debt, growth rates: same rule — show "derived from FY2025 10-K"
    or "assumed" with the flag.
14. Reconciliation doc: `docs/DATA_VERIFICATION.md` listing each input, its source, its check,
    and how to reconcile against Yahoo/Morningstar figures (and why they may legitimately differ).

**Also carried from Sprint 1**: physical-device test on iPhone + Android (#19), Android share
card visual check (#22), multi-class share handling via dimensioned XBRL (#1) — required for BRK,
GOOG, META once the fetch is on-device.

### Sprint 3 — Every ticker gives an honest answer ✅ (2026-09-21)
Successor-issuer resolution, sector modes, multi-class shares, normalized working capital. See TASKS.md / SPRINTS.md.

### Sprint 4 — Release readiness + concept-map feedback loop
Opt-in gap reporting → weekly human review → reviewed map changes (never automatic); scheduled
concept-coverage probe; TestFlight / Play internal track; crash reporting; accessibility; store
assets; licensed quote/beta source.

### Sprint 5 — Two layouts, one set of numbers (2026-09-22, in progress)
A design and UI sprint. A second presentation ("report card": light, graded health facts) arrives
**beside** the existing dark layout rather than replacing it, chosen in Settings. Theme becomes
independent of layout (light/dark/system, with a user-chosen accent later), which requires the one
global change in the sprint: `Theme`'s compile-time constants become runtime-resolved tokens.
The four trust-carrying elements become required components with a test, because two layouts means
two places they can be dropped. Reasoning and decisions: `docs/DESIGN.md`.

### Later
Filing drill-down, notes per company, side-by-side comparison, Apple Watch complication,
a shared cache/batch screener *if* a server is ever justified (the Dockerfile stays in the repo).

## Non-goals (permanent)
Chart patterns, moving averages, RSI/MACD/oscillators, volume analysis, momentum screens,
price alerts based on technical levels, anything that treats price history as signal.
