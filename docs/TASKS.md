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

## Sprint 2 — On-device engine, consumer clarity, verified data (2026-09-19) ✅ (owner actions pending: Pages + secret)

### A. On-device core (no hosted engine)
- [x] `packages/valuation-core` (KMP: android, jvm, iosArm64, iosSimulatorArm64 → `ValuationCore.xcframework`)
- [x] Ported normalization (tags, statements, splits, fiscal-year rule, derived items) and Model A/B
- [x] EDGAR client in core (identity User-Agent, TTL cache, tickers mirror fallback, non-JSON guard, typed errors)
- [x] Oracle test vs Python on AAPL/KO/JNJ fixtures — 0 diffs
- [x] Android depends on the core; `EngineApi`/remote repository deleted
- [x] iOS embeds the XCFramework (xcodegen pre-build script builds it); `APIClient`/remote repository deleted
- [x] Engine URL / LAN / "engine offline" UI removed from both Settings screens
- [x] Python engine kept as reference/oracle (`pytest` green)

### B. Rates & tickers via GitHub Pages
- [x] `scripts/publish_rates.py` — verified locally with the FRED key (as_of 2026-09-17)
- [x] `.github/workflows/publish-data.yml` — weekday cron + Sunday tickers + manual dispatch → `gh-pages`
- [ ] **Owner**: add `FRED_API_KEY` repository secret; enable Pages on `gh-pages`; run the workflow once
- [x] `scripts/publish_tickers.py` (10,438 filers)
- [x] Apps read `rates.json` (12 h TTL, stale > 7 d flagged) with a bundled snapshot as fallback; "as of" shown in Settings
- [x] Core searches the `tickers.json` mirror first, SEC second

### C. Expert Mode & consumer clarity
- [x] Settings → Expert Mode toggle (persisted, default off) — both apps
- [x] Basic company screen: plain-language verdict sentence, compact gauge, checks summary, 4 health tiles with ⓘ explainers, "Show me the math"
- [x] Expert view: full metrics with Expand all / Collapse all, beta detail line, provenance-labeled assumptions
- [~] Case study copy — unchanged this sprint (already plain); expert variant is the existing metric rows
- [x] Glossary screen (10 terms, plain + expert), copy shared from the core (`Explain.kt`)
- [x] Basic-mode model names "Classic value" / "Cash-flow value" with blurbs
- [x] Watchlist row in basic mode shows price + verdict only
- [x] Web preview mirrors basic/expert toggle

### D. Data verification gate
- [x] `DataChecks` in core: 13 checks (see DATA_VERIFICATION.md); fail withholds the value in both apps
- [x] **Beta derived** from 5-yr monthly regression vs ^GSPC with R²/window; assumed 1.0 only with an amber flag (#31)
- [x] Tax rate, cost of debt, rates, price: provenance labels, never bare defaults
- [x] Freshest-tag selection for TTM (fixed PG cash, KO debt) + `tag_coverage` / `debt_coverage` checks
- [x] `docs/DATA_VERIFICATION.md`
- [ ] Multi-class shares via dimensioned XBRL (#1) — carried to Sprint 3

### Carried over
- [ ] Physical iPhone + Android test of the on-device path (#19 is moot; verify SEC/Yahoo reachability from a phone network)
- [ ] Android share-card PNG visual check (#22), light theme (#23), concurrent watchlist refresh (#24), chart axes (#25)

## Sprint 3 — Every ticker gives an honest answer (started 2026-09-21)

Coverage probe on day 1 (14 tickers): tech/consumer/healthcare correct; **XOM failed**
(holding-company reorg); **banks and REITs got confident but wrong verdicts**; BRK-B honest but empty.

### A. Filer identity resolution ✅
- [x] Successor-issuer fallback: when the resolved CIK has no 10-K data, find the predecessor via
      submissions (SIC, first filing, 8-K12B) + entity search; accept only on evidence (same SIC,
      recent 10-K that predates the successor). Core + Python reference, 5 + 3 tests.
- [x] `filer_identity` data check (warn) and warning line make the substitution visible; header shows the predecessor CIK
- [x] Regression guard: ordinary tickers make zero identity lookups; oracle unchanged
- [x] **Substitution requires an 8-K12B/8-K12G3 successor notice** — IPOs and same-name spin-offs never get another company's numbers (tested)
- [x] Honest no-data reasons from the filing profile: new listing (S-1/424B4), foreign private issuer (20-F/40-F), fund/trust (N-CSR), no XBRL
- [x] Negative DCF → null with note instead of a negative price (#51)
- [ ] Post-reorg 10-Qs are filed by the successor and not merged into the predecessor's TTM (ISSUES #46)

### B. Sector modes (banks, insurers, REITs)
- [ ] Sector detection from SIC (submissions API): banks 6020–6199, insurers 6311–6411, REITs 6798
- [ ] Financials mode: hide Model B / owner earnings / NNWC; value on book value + sustainable ROE (justified P/B) + Graham EPS; checks adapted (no classified balance sheet expected)
- [ ] REIT mode: FFO / AFFO from net income + depreciation − gains on sale; FFO multiple + dividend coverage; hide NNWC
- [ ] Unmapped sectors keep the general model but the checks say so
- [ ] Basic-mode copy per sector ("banks are valued on their book value…")

### C. Multi-class shares (#1)
- [ ] Per-class `dei` share counts via companyconcept/frames or the filing's instance; BRK-B, GOOG/GOOGL, META per-share values

### D. Model quality
- [ ] ΔNWC smoothing (3-yr average) for owner earnings (#2)
- [ ] Classic Graham value → expert-only row (revised is the composite input)

### Deferred to Sprint 4 (release readiness)
- [ ] TestFlight + Play internal testing; crash reporting; Dynamic Type / VoiceOver pass; store assets
- [ ] Licensed quote/beta source behind `Market`; physical-device checks (#19/#20/#22)

