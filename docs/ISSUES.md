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
| 45 | **P1** | core/engine | ~~XOM returned `no_annual_data`~~: SEC's ticker list maps reorganized companies to a new holding-company CIK with no 10-K history (ExxonMobil Holdings Corp, 8-K12B on 2026-07-01). | Successor-issuer fallback resolves the predecessor on evidence (same SIC, recent 10-K, 8-K12B) and flags it (`filer_identity`). | fixed |
| 46 | P3 | core/engine | After a holdco reorg the successor files the new 10-Qs, so the predecessor's TTM stops at the last pre-reorg quarter (`filing_freshness` warns). | Merge successor 10-Q facts onto the predecessor history. | open |
| 47 | P2 | core | Banks/insurers (JPM, BAC) get a confident Model A verdict from owner earnings/NNWC that don't apply to financials; Model B is null. | Sprint 3 Track B financials mode. | open |
| 48 | P2 | core | REITs (O, PLD) show Model A values ~15% of price because GAAP depreciation depresses earnings; FFO is the right basis. | Sprint 3 Track B REIT mode. | open |
| 49 | P3 | core | XOM measured beta 0.18 (5y monthly to 2026-09) is well below Yahoo's ~0.5; window/end-month differences plus energy's 2021–22 regime. Value is labeled measured with R², but worth a Sprint 4 cross-check against a licensed source. | | open |
| 50 | P3 | core | Entity-search predecessor lookup depends on `efts.sec.gov` (an undocumented EDGAR endpoint); if it changes, successor tickers regress to `no_annual_data` (honest, not wrong). | | accepted |
| 51 | P2 | core/engine | Model B showed a negative fair value (CRWV −$522/share) when projected free cash flow is negative — a nonsense number for a non-expert. | DCF now returns null with a note ("a DCF cannot value a business that consumes cash"); Model A still shown. Oracle unchanged. | fixed |
| 52 | P3 | core/engine | ETFs/trusts (SPY) 404 on companyfacts; previously a bare "no XBRL facts" message. | Now classified from the filing profile ("fund or trust, no 10-K to value"). | fixed |
| 47 | | | (see row 47 above — fixed by Sprint 3 Track B financial mode) | | fixed |
| 48 | | | (see row 48 above — fixed by Sprint 3 Track B REIT mode) | | fixed |
| 53 | P2 | core | KO's TTM ends 2026-04-03 although a June 10-Q should exist (`filing_freshness` warns at 171 days). Either the Q2 10-Q isn't in companyfacts yet or its revenue tag isn't in the fallback list. | Inspect KO's latest 10-Q tags. | open |
| 54 | P2 | core | Visa (multi-class A/B/C) now fails `share_count` and shows "insufficient data" — honest, but a top-20 company with no value until Track C lands. | Track C multi-class shares. | open |
| 55 | P3 | core | Insurers: ROE swings with AOCI (unrealized bond losses) and catastrophe years; a single TTM ROE drives the justified P/B. | Use 3–5 yr average ROE for the book-value model; add float/combined ratio metrics. | open |
| 56 | P3 | core | Sector policy constants (Ke floor rf+4%, P/B cap 4×, terminal spread cap 15 pts, mREIT D&A/revenue < 5%) are first guesses and not user-adjustable. | Expose in Settings → Assumptions (expert) after tester feedback. | open |
| 57 | P3 | core/engine | General Model B now floors CAPM at rf + 4%, departing from the blueprint's pure CAPM for low-beta stocks (KO β 0.29: DCF $26.9 → $14.2). Labeled on the metric. | Owner decision to keep or make optional. | accepted |
| 58 | P3 | engine | Python reference covers only the general models; sector models, data checks, beta and identity resolution exist in the Kotlin core alone. | Port when a second consumer needs them, or retire the Python engine to fixtures-only. | accepted |
| 59 | P3 | core | Conglomerates classified by a financial SIC (BRK 6331) get the bank/insurer models even though most of their value is operating businesses. | Manual sector override in Settings (expert). | open |
| 60 | P3 | core | Sector mode is decided from SIC alone; a company that changed business (e.g. GE → GE Aerospace) keeps its old code until SEC updates it. | Show SIC description in expert mode so the user can spot it. | open |
