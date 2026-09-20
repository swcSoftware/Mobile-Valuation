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

## Sprint 2 — On-device engine, consumer clarity, verified data

### A. On-device core (no hosted engine)
- [ ] Create `packages/valuation-core` (Kotlin Multiplatform, targets android + iosArm64/iosSimulatorArm64)
- [ ] Port `normalize/tags.py` + `statements.py` (concept map, annual/TTM, splits, derived items, fiscal-year rule)
- [ ] Port `valuation/model_a.py`, `model_b.py`, `types.py` (Metric with formula/inputs/sources)
- [ ] EDGAR client in core: User-Agent, 10 req/s limiter, on-device cache (24 h), `no_annual_data` etc. as core errors
- [ ] Cross-implementation test: Python vs KMP on AAPL/KO/JNJ fixtures, diff within 1e-6
- [ ] Android: depend on the core module; delete `EngineApi`/`RemoteValuationRepository`
- [ ] iOS: XCFramework via `./gradlew :valuation-core:assembleXCFramework`, Swift wrapper conforming to `ValuationRepository`; delete `APIClient`/remote repo
- [ ] Remove engine URL / LAN / "engine offline" UI from both Settings screens
- [ ] Python engine: mark as reference/oracle in README; keep `pytest` green

### B. Rates & tickers via GitHub Pages
- [ ] `scripts/publish_rates.py` (FRED DAAA/DGS10 → `rates.json` with 30-day history)
- [ ] `.github/workflows/rates.yml` cron weekdays 11:00 UTC, `FRED_API_KEY` repo secret, commit to `gh-pages`
- [ ] Enable GitHub Pages on `gh-pages` (owner action) and document the URL
- [ ] `scripts/publish_tickers.py` weekly mirror of SEC `company_tickers.json`
- [ ] Apps: fetch `rates.json` on launch (12 h TTL), cache, "as of" label, stale (>7 d) amber note, override wins
- [ ] Apps: search uses cached `tickers.json`; instant + offline

### C. Expert Mode & consumer clarity
- [ ] Settings → **Expert Mode** toggle (persisted), default off
- [ ] Basic company screen: price, plain-language fair-value sentence per model, MoS bar, verdict, 4 health facts
- [ ] ⓘ explainers (≤ 60 words, no formulas) on every basic-mode number
- [ ] Expert mode: full detail + **Expand all / Collapse all** for metric disclosures
- [ ] Case study: general-audience copy with "Show me the math" expert variant
- [ ] Glossary screen (Settings → Glossary)
- [ ] Basic-mode model names: "Classic value" / "Cash-flow value"; expert keeps Model A/B labels
- [ ] Watchlist row in basic mode: verdict sentence instead of "$269.99 vs $74.08"
- [ ] Web preview mirrors basic/expert toggle for tester feedback

### D. Data verification gate
- [ ] `DataCheck` framework in core: pass/warn/fail, message, affected inputs
- [ ] Checks: balance-sheet identity, EPS ≈ NI/shares, share-count/market-cap plausibility, TTM period alignment, freshness (filing ≤ 130 d, price ≤ 5 d), sign/non-negativity, provenance for every input
- [ ] "Data checks" card (basic: one-line summary; expert: full list) shown above valuation; **fail blocks the fair-value number** with an explanation
- [ ] **Beta derived, not assumed** (#31): 5-yr monthly regression vs S&P 500 from keyless price history; show method, window, R²; "assumed β 1.0" amber flag only when < 36 months of data
- [ ] Tax rate, cost of debt, growth: label "derived from <filing>" vs "assumed", never a bare default
- [ ] Price freshness + source shown next to price; stale price blocks MoS verdict (shows "enter price")
- [ ] `docs/DATA_VERIFICATION.md`: every input → source → check → how to reconcile with Yahoo/Morningstar
- [ ] Multi-class shares via dimensioned XBRL (`frames` API or instance doc) — BRK, GOOG, META (#1)

### Carried over
- [ ] Physical iPhone + Android test (#19 becomes moot once on-device; verify EDGAR direct from device)
- [ ] Android share-card PNG visual check (#22)
- [ ] Android light theme (#23), concurrent watchlist refresh (#24), chart axes (#25)

## Sprint 3 — Polish & beta
- [ ] TestFlight + Play internal testing
- [ ] Crash reporting, engine error telemetry (no user analytics)
- [ ] Dynamic Type / VoiceOver pass
- [ ] App Store screenshots, privacy nutrition labels (identity stored on device only)
