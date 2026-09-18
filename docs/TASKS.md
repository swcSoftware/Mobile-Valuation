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

## Sprint 1 — Any ticker, anywhere

### Engine
- [ ] Containerize (Dockerfile) and deploy to a hosted URL (Fly.io / Railway / Cloud Run) — see PATH_TO_ANY_TICKER.md
- [ ] Persistent cache (SQLite or Redis) instead of local `.cache/`
- [ ] Proper error taxonomy (`no_annual_data`, `no_share_count`, `upstream_unavailable`) surfaced to clients
- [ ] `FRED_API_KEY` set in hosted env; verify `rate_source: FRED` end to end
- [ ] Licensed / official price provider behind `PriceProvider` (see ISSUES #3)
- [ ] Rate-limit per client identity, request logging

### iOS
- [ ] Settings → engine URL defaults to hosted URL in Release builds
- [ ] Watchlist auto-refresh on foreground + last-updated stamp
- [ ] Empty/error states for `insufficient_data` (per-share values unavailable) with explanation
- [ ] Deep link `valuelens://ticker/AAPL`
- [ ] Haptics on verdict change; MoS gauge accessibility label
- [ ] Case study: choose *any* ticker from search, not just three presets

### Android
- [ ] Generate Gradle wrapper, build in Android Studio
- [ ] EncryptedSharedPreferences identity + onboarding
- [ ] Port screens: watchlist, search, company detail (Model A/B toggle, MoS gauge, metric disclosure), settings
- [ ] PDF (android.graphics.pdf) + share card (Bitmap) + system share sheet
- [ ] Bundled sample JSON fallback

## Sprint 2 — Data quality & model depth
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
