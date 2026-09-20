# Task Board

Status: `[x]` done · `[ ]` todo · `[~]` in progress. Move finished sprints to SPRINTS.md.

## Sprint 0 — Alpha skeleton (2026-09-18) ✅

### Engine
- [x] SEC-compliant EDGAR client (User-Agent passthrough, rate limit, disk cache)
- [x] Ticker → CIK resolution and search
- [x] companyfacts parsing → canonical concepts with tag fallbacks
- [x] Annual (10-K) series up to 10 years; TTM from 10-K + 10-Q
- [x] Split adjustment, stale-tag guard, multi-class share summing, EBIT fallback
- [x] Model A: Graham classic + revised, NNWC, owner earnings, MoS bands + verdict
- [x] Model B: CAPM/WACC, FCFF DCF (perpetuity + exit multiple), ROIC, EVA, moat persistence
- [x] Price providers (Yahoo, Stooq, manual) + FRED rates with defaults
- [x] FastAPI routes + formula unit tests (3)

### iOS
- [x] xcodegen project, iOS 17, dark theme, design system
- [x] Identity onboarding → Keychain
- [x] Interactive case study (4 steps, AAPL/KO/MSFT)
- [x] Watchlist (persisted), Search (live SEC list), Settings (identity, engine URL, assumptions)
- [x] Company detail: MoS gauge, Model A/B toggle, metric disclosure, snapshot, charts, 10-yr table, growth, assumptions, warnings
- [x] Market price override sheet
- [x] PDF dossier + 1:1 / 16:9 share cards → share sheet / Files
- [x] Offline fallback to bundled sample JSON
- [x] Verified in iPhone 16 Pro simulator (onboarding → JNJ live valuation → export)

### Android
- [x] Gradle/Compose scaffold, Kotlin contract models, OkHttp client stub

### Repo
- [x] `main` / `staging` / `dev` branches; docs folder

## Sprint 1 — Any ticker, anywhere (2026-09-19) ✅ (hosting deferred by owner decision: LAN mode)

### Engine
- [~] Containerize and deploy — **Dockerfile + fly.toml + `scripts/serve-lan.sh` written; owner chose LAN-only for now.** Deploy is a one-command step when wanted (PATH_TO_ANY_TICKER.md).
- [x] Persistent cache: SQLite (`CACHE_DB`), WAL mode, TTL on read, stats in `/health`
- [x] Error taxonomy (`unknown_ticker`, `no_annual_data`, `upstream_unavailable`, `rate_limited`, `invalid_identity`) with stable wire format; both clients map to typed errors
- [x] `FRED_API_KEY` set locally; verified `rate_source: FRED` end to end (AAA 5.94 / 10Y 4.94 on 2026-09-17)
- [x] Licensed price provider: Polygon.io behind `POLYGON_API_KEY` (respx-tested; no key yet → Yahoo/Stooq)
- [x] Per-identity sliding-window rate limit, optional `REQUIRE_IDENTITY`, access log with latency + identity
- [x] `/health` reports rates source, providers, cache stats, LAN addresses
- [x] 26 offline tests on recorded companyfacts fixtures (AAPL, KO, JNJ)

### iOS
- [x] Engine URL default per build config (Info.plist `ENGINE_BASE_URL`); Release placeholder until hosted (ISSUES #18)
- [x] Settings: LAN address picker from `/health`, reset to default
- [x] Watchlist: concurrent refresh, foreground auto-refresh (>15 min), last-updated stamp
- [x] Typed error states with titles/icons; `InsufficientDataCard` explains missing per-share values
- [x] Deep link `valuelens://ticker/AAPL` (AppRouter) — verified in simulator
- [x] Haptics on verdict change; MoS gauge + watchlist rows VoiceOver labels
- [x] Case study: any ticker via live search
- [x] `ValueLensTests` target: 10 unit tests

### Android
- [x] Gradle wrapper generated; builds headless (`./gradlew assembleDebug`) with Android Studio's JDK 21
- [x] EncryptedSharedPreferences identity + onboarding (identity → case study, presets + any ticker)
- [x] Screens ported: watchlist, search, company detail (Model A/B toggle, MoS gauge, metric disclosure, chart, table, growth, assumptions), settings
- [x] PDF (PdfDocument) + share cards (Bitmap) + system share sheet via FileProvider
- [x] Bundled sample JSON fallback (assets/)
- [x] Deep link, foreground refresh, typed errors, haptics
- [x] 7 JVM unit tests; verified live (JNJ, KO) on Pixel 10 emulator

## Sprint 2 — Data quality & model depth
- [ ] Host the engine (Fly.io/Railway) and set the Release `ENGINE_BASE_URL` (carried from Sprint 1)
- [ ] Verify LAN mode on a physical iPhone + Android phone (ATS for private IPs, ISSUES #19)
- [ ] Android: share-card PNG visual check; light theme; Compose UI tests
- [ ] Multi-class shares via dimensioned facts (`frames` API or full XBRL instance) — BRK, GOOG, META
- [ ] Sector tag maps: banks/insurers (no operating income, different working capital), REITs (FFO)
- [ ] ΔNWC smoothing (3-yr average) for owner earnings — KO 2025 swing
- [ ] 10-Q trajectory panel: TTM vs last 10-K for revenue, margins, CFO (blueprint §3.2)
- [ ] User-selectable maintenance-capex method (D&A / avg capex / Greenwald)
- [ ] `/companies/{ticker}/financials` consumed by iOS for a full statement browser
- [ ] Engine unit tests with recorded companyfacts fixtures (respx)

## Sprint 3 — Polish & beta
- [ ] TestFlight + Play internal testing
- [ ] Crash reporting, engine error telemetry (no user analytics)
- [ ] Dynamic Type / VoiceOver pass
- [ ] App Store screenshots, privacy nutrition labels (identity stored on device only)
