# Known Issues

Minor defects noticed during development are logged here rather than fixed immediately (owner
instruction for alpha). Severity: **P1** blocks core flow · **P2** wrong number shown · **P3** cosmetic.

| # | Sev | Area | Description | Repro / notes | Status |
|---|---|---|---|---|---|
| 1 | P2 | engine | Multi-class filers (BRK-B, GOOG) have no undimensioned per-share facts in `companyfacts`; per-share values and DCF come back `null` with verdict `insufficient_data`. | Search BRK-B. Needs dimensioned XBRL (Sprint 2). | open |
| 2 | P2 | engine | Owner earnings swing wildly when ΔNWC has a one-off (KO FY2025 OE $3.2B vs $15.3B prior). | Use 3-yr average ΔNWC or exclude acquisition-related current liabilities. | open |
| 3 | P2 | engine | Yahoo chart quote endpoint is unofficial and may break or rate-limit; Stooq currently returns 404 from this network. | Manual price override works as fallback. Replace with licensed provider before beta. | open |
| 4 | P3 | engine | `Latest 10-K missing concepts` warning lists optional concepts (goodwill, dividends) alongside important ones. | Split into required vs optional. | open |
| 5 | P3 | engine | Graham `g` for AAPL clamps at 15% because split-adjusted EPS CAGR is 17.9%; the note explains it but the clamp is a policy choice worth a Settings toggle. | | open |
| 6 | P3 | engine | Cost of debt uses `interest_expense / total_debt`; AAPL doesn't tag InterestExpense in recent 10-Ks so it falls back to rf+1.5%. | Add `InterestExpenseNonoperating` variants / `InterestPaidNet`. | open |
| 7 | P3 | ios | Segmented control labels truncated on narrow widths in earlier build; fixed by moving subtitle below control. | | fixed |
| 8 | P3 | ios | Case-study page dots overlapped last card row; fixed with extra bottom padding. | | fixed |
| 9 | P3 | ios | `Text("\(Int)")` locale-formats numbers ("FY2,016"); fixed by `String(int)`. Watch for regressions. | | fixed |
| 10 | P3 | ios | History chart x-axis shows two-digit years; fine for 10 years, ambiguous for longer windows. | | open |
| 11 | P3 | ios | Keyboard does not auto-dismiss after entering identity; tap outside. | Add `.scrollDismissesKeyboard(.interactively)`. | open |
| 12 | P3 | ios | Watchlist pull-to-refresh is sequential; 10 tickers ≈ 10 round trips. | Now a TaskGroup (Sprint 1). Android is still sequential (see #24). | fixed |
| 13 | P3 | ios | Keychain writes require a signed build; `CODE_SIGNING_ALLOWED=NO` builds silently fail to persist identity. | Documented in RUNBOOK. | documented |
| 14 | P3 | ios | ImageRenderer PDF is single-page; long histories will clip past 792pt. | Paginate in Sprint 3. | open |
| 15 | P3 | android | Scaffold has never been compiled; dependency versions are best-effort. | | open |
| 16 | P2 | engine | JNJ (and other 52/53-week filers) had two fiscal years mapped to the same label, double-counting in charts. | Fixed via `fiscal_year_for()`. | fixed |
| 17 | P3 | engine | EPS TTM is computed additively (FY + YTD − prior YTD); exact only when share count is stable. Note is shown on the metric. | | accepted |
| 18 | P2 | ios | ~~Release build's default engine URL is a placeholder~~ Obsolete: no engine URL since Sprint 2 (on-device). (`https://valuelens-engine.fly.dev`) that doesn't exist yet. Users can override in Settings. | | closed |
| 19 | P2 | ios | ~~LAN mode on a physical iPhone is untested~~ Obsolete (on-device); replaced by a physical-device check of SEC/Yahoo reachability.: ATS `NSAllowsLocalNetworking` should cover private IPs over http, but if not, Debug needs `NSAllowsArbitraryLoads`. | | closed |
| 20 | P3 | ios | Haptics can't be felt in the simulator; verify on device. | | open |
| 21 | P3 | android | `EncryptedSharedPreferences` (security-crypto 1.1.0-alpha06) logs a deprecation warning; falls back to plain prefs if keystore init fails (logged, not surfaced). | Migrate to Jetpack DataStore + Tink or Android Keystore direct in Sprint 2. | open |
| 22 | P3 | android | Share-card PNG export runs but was not visually verified (PDF was). | Pull from `Android/data/…/Documents/ValueLens` and inspect. | open |
| 23 | P3 | android | Light theme not implemented (dark-first by design; `isSystemInDarkTheme()` unused). | | open |
| 24 | P3 | android | Watchlist refresh is sequential; iOS uses a TaskGroup. | Use `async`/`awaitAll`. | open |
| 25 | P3 | android | History chart has no y-axis labels beyond the max value; x-axis uses 2-digit years. | Match iOS chart axes. | open |
| 26 | P3 | android | Deep link when the app is already open relies on `singleTask` + `onNewIntent`; back stack after a deep link returns to the tab root, not the previous screen. | | open |
| 27 | P3 | engine | Rate limiter and FRED cache are in-process; multiple hosted instances would each have their own window. | Redis when scaling past one instance. | accepted |
| 28 | P3 | engine | `/health` calls FRED on every request (cached 6 h); with a bad key each call retries. | Cache failures too. | open |
| 29 | P3 | engine | Test suite depends on `CACHE_DB` env being set before import (conftest does it); running a single test file with a different cwd could touch the dev cache. | | accepted |
| 30 | P3 | both | Bundled sample JSON captured with FRED rates of 2026-09-17; will drift from live values. | Refresh via RUNBOOK §5 each sprint. | accepted |
| 31 | **P2** | engine/both | ~~**Beta is silently assumed = 1.0**~~ Fixed Sprint 2: measured 5-yr monthly regression beta with R²; assumed only with an amber flag. for every company (it is not an SEC datum, and no price-history source was wired). Yahoo shows e.g. AAPL ≈ 1.2, KO ≈ 0.6, so WACC and every Model B fair value are off. The Assumptions card does list "Beta 1.00" but nothing marks it as assumed rather than measured. | | fixed |
| 32 | P2 | both | No pre-calculation data validation: a wrong share count, mismatched TTM periods or a stale price flows straight into the fair-value number. | 13-check gate in the core; fail withholds the value. | fixed |
| 33 | P3 | both | Presentation is expert-only: formulas, XBRL tags and 15+ metric rows are the default view. | Expert Mode (default off) + basic view. | fixed |
| 34 | P3 | engine | Hosting artifacts (Dockerfile, fly.toml, middleware rate limiter) become non-critical once the core moves on-device; keep but don't extend. | | accepted |
| 35 | **P2** | core | TTM lookup took the first tag with any data and could then drop it as stale — KO lost $39B of debt (ROIC 52%→19%), PG lost cash. | Freshest tag across candidates now wins; `tag_coverage`/`debt_coverage` checks added. | fixed |
| 36 | P3 | core | Beta uses Yahoo's unofficial chart endpoint for both the stock and ^GSPC; a rate limit or endpoint change silently degrades to "assumed β". | The assumed flag makes it visible; licensed source in Sprint 3. | open |
| 37 | P3 | ios | `explain()` on a watchlist-cached report (no raw JSON) falls back to a Swift re-encode that doesn't round-trip `treasury_10y_pct`; the detail screen always reloads fresh so users don't see it, but the fallback path returns nil. | Store raw JSON in the watchlist entry or add a Kotlin-side key mapping. | open |
| 38 | P3 | ios | XCFramework is a build artifact (18 MB) built by Gradle; first Xcode build needs Android Studio's JDK or JAVA_HOME. | Documented in RUNBOOK; consider committing a prebuilt framework per release. | accepted |
| 39 | P3 | android | Unit tests read samples from `src/main/assets` by relative path; running from another working dir fails. | Use classpath resources. | open |
| 40 | P3 | both | Bundled samples were generated 2026-09-19 with live prices/beta; they drift from reality until regenerated (RUNBOOK §5). | | accepted |
| 41 | P3 | core | `Edgar.masterList()` parses the full 800 KB ticker list on every search keystroke (debounced 300 ms); fine on modern phones, wasteful. | Cache the parsed list in memory. | open |
| 42 | P3 | core | Check thresholds (0.5% balance-sheet tolerance, 10% EPS tolerance, 130-day freshness) are first guesses; expect false warnings on 52/53-week filers around year end. | Tune after tester feedback. | open |
| 43 | P3 | web | Web preview's basic/expert toggle is a static mirror; it does not run the core. | | accepted |
| 44 | P2 | ci | First Pages deploy used `keep_files: true` on a `gh-pages` branch created from `dev`, so the whole source tree was published to the public Pages site. | Workflow now `keep_files: false` (replaces the branch with `site/` only). Owner: run "Publish data files" once to prune. | fixed |
