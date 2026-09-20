# Architecture

```
┌─────────────┐   X-SEC-User-Agent    ┌──────────────────────────────┐   User-Agent   ┌───────────┐
│ iOS/Android │ ───── JSON/HTTP ────▶ │ valuation-engine (FastAPI)   │ ─────────────▶ │ SEC EDGAR │
│  (native)   │ ◀──── report ──────── │ edgar → normalize → valuation│ ◀── XBRL ───── │ companyfacts│
└─────────────┘                       │ providers: prices, rates     │                └───────────┘
                                      └──────────────────────────────┘ ──▶ Yahoo/Stooq, FRED
```

## services/valuation-engine (Python 3.11+)

| Module | Responsibility |
|---|---|
| `edgar/client.py` | HTTP with SEC-compliant `User-Agent`, 8 req/s limiter, SQLite JSON cache (24h, `cache.py`) |
| `errors.py` | Error taxonomy → stable wire codes (API.md) |
| `middleware.py` | Access log, identity validation, per-identity sliding-window rate limit |
| `edgar/tickers.py` | `company_tickers.json` → ticker/CIK/name; search |
| `edgar/companyfacts.py` | Parse `companyfacts` into flat `Fact` records (tag, unit, value, start/end, form, accession, filed) |
| `normalize/tags.py` | Canonical concept → ordered XBRL tag fallbacks (revenue, net_income, cfo, capex, …) |
| `normalize/statements.py` | Annual (10-K) periods, TTM (10-K + 10-Q), split adjustment, derived items, CAGR |
| `valuation/model_a.py` | Graham classic/revised, NNWC, owner earnings, MoS |
| `valuation/model_b.py` | CAPM/WACC, 5-yr FCFF DCF (perpetuity + exit), ROIC, EVA, moat persistence |
| `valuation/engine.py` | Orchestration + payload assembly |
| `providers/prices.py` | `PriceProvider` protocol; Polygon (keyed) → Yahoo chart → Stooq; manual override wins |
| `providers/rates.py` | FRED DAAA / DGS10 with cached defaults |
| `main.py` | FastAPI routes (see API.md) |

### Ingestion rules (the part most likely to need revisiting)
1. **Annual flows**: 10-K facts with 340–380-day duration, keyed by period end; latest `filed` wins (restatements).
2. **Annual instants**: 10-K facts with no `start` whose `end` equals a fiscal-year end.
3. **Fiscal-year label**: `end.year`, minus one when the period ends Jan 1–7 (52/53-week years, e.g. JNJ).
4. **TTM flows**: `FY + YTD(latest 10-Q) − YTD(prior-year comparable)`; shares use latest YTD weighted average.
5. **TTM instants**: latest balance-sheet date from any 10-K/10-Q.
6. **Staleness**: any TTM value >540 days older than the newest one is dropped (tag no longer used).
7. **Splits**: a ≥1.8× (or ≤0.55×) jump in diluted shares between consecutive years rescales all earlier per-share data.
8. **Shares outstanding**: `dei:EntityCommonStockSharesOutstanding` (summed across classes at the latest date); ignored if >540 days stale; falls back to diluted weighted average.

Every value the models consume is a `SourcedValue` (tag, accession, form, period, filed, derived, note),
and every output is a `Metric` (value, unit, formula, inputs, sources, notes). That is the
blueprint's "deterministic and transparent" mandate made structural.

### Why no Celery / pandas yet
A single `companyfacts` call plus pure-Python arithmetic completes in well under a second once
cached. Introduce a task queue when batch screening or scheduled refreshes exist.

## apps/ios (Swift 5.10, SwiftUI, iOS 17)

```
App/            ValueLensApp, RootView (onboarding gate), MainTabView
Domain/         Codable models mirroring API.md; ValuationRepository protocol; RateOverrides
Data/           APIClient, RemoteValuationRepository (+ Sample fallback), KeychainStore,
                AppSettings (@Observable), WatchlistStore (JSON in Application Support)
DesignSystem/   Theme (dark, amber=price, mint=value), Fmt, Card/Pill/SectionHeader,
                MetricRow (formula/inputs/sources disclosure), MarginOfSafetyView
Features/       Onboarding (Identity, CaseStudy), Watchlist, Search, Company (detail, charts,
                table), Settings, Export (PDF dossier, share card, ShareSheet)
Resources/      Assets, bundled SampleData/{AAPL,KO,MSFT}.json
```
MVVM: views own `@State` view models (`CompanyViewModel`) that call the repository. Repository
pattern lets the sample data stand in for the network transparently.

**Gotcha**: `JSONDecoder.convertFromSnakeCase` turns `treasury_10y_pct` into `treasury10YPct`
(capital Y). Property is named accordingly.

## apps/android (Kotlin 2.1, Jetpack Compose, minSdk 26)

```
ValueLensApplication.kt   AppState: identity (EncryptedSharedPreferences), prefs, watchlist, engine health,
                          deep-link pending ticker — the counterpart of iOS AppSettings + WatchlistStore + AppRouter
domain/Models.kt          kotlinx.serialization mirror of API.md + Verdict, ValuationModel, RateOverrides, EngineException
data/                     EngineApi (OkHttp, X-SEC-User-Agent), Remote/Sample repositories, stores
ui/theme, ui/Fmt.kt       Same tokens and number formatting as iOS
ui/components             Card, Pill, ModelToggle, MetricRow (disclosure), MarginOfSafetyView
ui/screens                Onboarding (Identity, CaseStudy), Watchlist, Search, CompanyDetail, Settings
ui/ValueLensApp.kt        NavHost + bottom NavigationBar; deep-link navigation
export/Exporter.kt        PdfDocument dossier, Bitmap share cards, FileProvider share intent
assets/                   AAPL/KO/MSFT sample JSON (offline fallback)
```
Emulator reaches the host engine at `http://10.0.2.2:8000` (default). Physical device: LAN address
from Settings.
