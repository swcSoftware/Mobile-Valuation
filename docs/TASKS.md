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

### B. Sector modes (banks, insurers, brokers, REITs) ✅
- [x] Sector detection from SIC (submissions profile, cached): financial 6020–6199, 6200–6299, 6311–6411, 6712; REIT 6798 (mortgage REITs → financial via a no-depreciation heuristic)
- [x] Financial Model A: Graham EPS + book value × justified P/B ((ROE−g)/(Ke−g), capped 4×); Model B: residual income (ROE−Ke spread, payout-driven book growth, terminal spread capped 15 pts); owner earnings / NNWC / FCFF hidden
- [x] REIT Model A: Graham on FFO/share (NI + D&A − gains on sale); Model B: FFO multiple (uses the exit-multiple setting) + dividend discount; dividend coverage
- [x] Cost of equity floored at rf + 4% (sector models and general Model B; Python mirrored; oracle unchanged)
- [x] `sector_mode` check (warns when the SIC is unknown); `debt_coverage` / `cost_of_debt` adapted for financials
- [x] Sector-aware basic copy: model blurbs, "Industry mode" line, ROE / leverage health tiles
- [x] Verified live: JPM, BAC, WFC, GS, SCHW, PGR, O, PLD, AGNC; AAPL control unchanged
- [ ] Insurers: float/combined-ratio view (ISSUES #55); Python reference lacks the sector models (#58)

### C. Multi-class shares (#1) ✅
- [x] Targeted XBRL-instance scanner: per-class cover counts, trading symbols, EPS (BRK, V, GOOG, META, NWS, FOX, LEN, UAA verified live)
- [x] Conversion ratio from per-class EPS; one company value in the searched ticker's share terms (BRK-A ≡ BRK-B × 1,500.24)
- [x] Trigger only on missing/stale/inconsistent counts; instance cached 30 days; `index.json` fallback for instance naming
- [x] `share_classes` data check (warns when a ratio had to be assumed 1:1); TTM EPS derived for per-class-only filers
- [x] Expert-mode "Share classes" card in both apps

### D. Model quality ✅
- [x] ΔNWC normalized (Damodaran): 5-yr average NWC ÷ revenue × Δrevenue (TTM annualized); fallbacks 3-yr mean → raw, all labeled; feeds owner earnings and FCFF. Python reference + Kotlin (oracle regenerated). KO OE no longer whipsaws (#2)
- [x] `working_capital` check flags one-offs (raw vs normalized > 30% of net income)
- [x] Classic (1962) Graham row removed from the guided case study; remains in the expert metric list. Revised is the composite input.
- [x] **Headline values changed for most companies** (AAPL Model A $72 → $97, KO $16 → $33, JNJ $61 → $101): the old figures carried single-year working-capital swings

## Sprint 4 — Release readiness + concept-map feedback loop (planned)

### A. Concept-map feedback loop (owner-approved design, 2026-09-21)
The tag map (`Concepts.kt` / `tags.py`) is hand-curated; gaps are found by users, not by us.
Users decide *where we look*; a human review decides *what changes*. Nothing alters a valuation
until a reviewed map change passes the oracle and tests.
- [x] `Coverage` in the core: `CoverageGap` / `CoverageReport` (ticker, CIK, period, concept, kind, candidate tags, app + map version). No user data. Attached to every report.
- [x] **Requirement levels** on every concept (`required` / `expected` / `optional`) in both maps, sector-downgraded (a bank has no capex) — cuts the noise that made gap reports unusable and fixes the warning in ISSUES #4
- [x] Candidate suggestion: head-noun + stem matching, filtered by fact kind and unit, so `InterestExpense` is never offered as `revenue`
- [x] **Report transport: prefilled GitHub issue URL** — no token in the binary, no server, and the user reads the payload in their browser before submitting. (Supersedes "POST to an endpoint / GitHub API"; both needed infrastructure we deliberately don't have.)
- [x] In-app "Unmatched line items" card (both apps) + "Report a data problem" in the export menu
- [x] `scripts/coverage_probe.py` + `scripts/universe.txt` (77 tickers): sector-aware, uses the app's `load_financials` so successor resolution applies; writes `docs/COVERAGE.md` + `docs/coverage.json`
- [x] Regression detection: fails when a company loses a concept it previously resolved (the ISSUES #35 class of bug)
- [x] `.github/workflows/coverage.yml` — Mondays 13:00 UTC + manual; commits the report to `dev`, fails loudly on regression
- [x] **Concept-map drift test**: parses `Concepts.kt` and `tags.py` and asserts identical concepts, order, tags, unit, taxonomy and requirement (mutation-tested). Gradle now tracks both as test inputs so they can't go stale
- [ ] Deferred: publish a versioned `concepts.json` to GitHub Pages so map fixes ship between app releases (only worth it once releases are gated — Track B below)

**First probe results** (77 tickers, all valued, 0 regressions): 1 required gap (AGNC revenue) and 5
`capex` gaps. See `docs/COVERAGE.md` and ISSUES #71–74.

### B. Release readiness
- [ ] TestFlight + Play internal testing; crash reporting; Dynamic Type / VoiceOver pass; store assets
- [ ] Licensed quote/beta source behind `Market`; physical-device checks (#19/#20/#22)

## Sprint 5 — Two layouts, one set of numbers (planned, 2026-09-22)

A **design and UI sprint**. No model, normalization or tag-map work. The owner picked the
"report card" direction (light, graded health facts) from three explored directions
(canvas: `ValueLens Directions`, three phone artboards driven by real engine output for
AAPL/MCD/BRK-B/JPM/PGR/O/AGNC/CRWV/PLTR).

**The shape of the work, decided up front:** this is an **add-on, not a replacement**
(non-negotiable 4). The current dark layout stays exactly as it is and remains the default until
the new one is finished; the new layout arrives beside it; a Settings toggle picks between them.
Nobody's existing screen changes behaviour because we added a second one.

**Why two and not three:** A and C are the same information design in different clothes (a
card-and-section stack; the difference is theme, type and density), so once theming is runtime the
second one is nearly free. B ("The Gap" — the price/value gap drawn as a measuring instrument) is a
genuinely different information design and stays in the canvas as a future direction, unbuilt.

### The four things every layout must carry

These are the reasons the app is trustworthy, and they are the things a second layout can silently
drop. Today each lives in exactly one place in `CompanyDetailView`; with two layouts that becomes
two. So they stop being inline markup and become **components a layout is required to place**:

1. **Value withheld** — `checksFailed`, and `verdict == .insufficientData`. It must read as a
   deliberate act of honesty, not an error or an empty state. (CRWV on Model B is the live case: the
   DCF comes out negative, so no number is shown.)
2. **Data notes and one-off flags** — `warnings` (MCD's share-scale correction, BRK-B's derived TTM
   EPS, PLTR's split restatement) and the non-passing `dataChecks`.
3. **Assumed vs measured** — `provenance`. Beta is measured; cost of debt often is not. An
   unlabeled input is a bug (non-negotiable 2, ISSUES #31).
4. **Sector mode** — an operating company, a bank and a REIT are not valued the same way and the
   screen says which.

- [x] A shared test asserts **both** layouts render all four, for a fixture of each case.
      **iOS: done** — `LayoutContractTests` collects what each layout placed through a SwiftUI
      preference and asserts nothing the report demands was dropped. Mutation-tested: removing
      `ProvenanceRow` fails with "Report card dropped provenance for CRWV".
      **Android: the demand logic is mirrored and unit-tested, but the rendering assertion needs
      Compose UI testing, which the module has no dependency on — ISSUES #87.**

### A. The layout seam (additive)
- [x] `LayoutStyle` enum: `.classic` (default) and `.reportCard`. Persisted in `AppSettings` and
      Android `AppPrefs` beside `expertMode`.
- [x] `CompanyDetailView` and `CompanyDetailScreen` split into a **container** and a **body**
      chosen by `LayoutStyle`. Today's body moved into `ClassicLayout` on both platforms.
      One deliberate behaviour change, not pure motion: data notes were expert-only, so a
      basic-mode reader was never told about MCD's share-scale correction. They now render in
      both modes — the exact omission the `dataNotes` obligation exists to prevent.
- [x] Layouts read only `ValuationReport`, `ModelResult` and `ExplainSummary`, through a narrow
      `LayoutContext`. Promoted to CLAUDE.md non-negotiable 7.
- [x] Expert Mode is **one shared presentation** (`ExpertBody`), not restyled per layout.

### B. Runtime theming (the one global change)
`Theme` is an `enum` of `static let` constants referenced 193× across 15 files, so a light layout is
impossible without this. This is a **global** change under non-negotiable 4 and gets its own commit
saying so.

**Three independent axes** (owner decision 2026-09-22 — not "layout implies theme"):

| Axis | Chosen by | Controls |
|---|---|---|
| **Theme** | Light / Dark / System | ground, text, and the semantic colors adjusted per ground |
| **Accent** | the user, eventually any color | chrome only: buttons, tabs, selection, focus |
| **Layout** | Classic / Report card | typography and components. **Never sets a color token.** |

- [x] Tokens resolved at runtime. **iOS**: `Theme`'s members became computed from a palette with a
      single writer, so all 193 call sites stayed as they were; the invariant (one writer, which
      bumps the generation the root uses as its `.id`) is documented and tested. **Android**: a
      `CompositionLocal`, which gives real recomposition for free — all 230 call sites unchanged.
      Verified in the simulator: dark is pixel-identical to before the refactor.
- [x] Four combinations hold. Classic-on-light checked in the simulator; the other three render.
- [x] **`price` and `value` are never accented**, asserted for every preset on both palettes and
      both platforms. Per-theme values: mint `#0B7A57` and amber `#9A6006` on paper.
- [x] Accent foreground computed from relative luminance, tested both ways.
- [x] Contrast-checked at 3:1 before it is applied; below that the swatch is shown as unavailable
      with the reason. Visible in the simulator: Slate is unavailable on dark; Mint, Azure and
      Ochre are unavailable on light.
- [x] Android `ui/theme/Theme.kt` has the same seam, plus `Palette.kt` mirroring `ThemePalette`.

### C. The report-card layout

**Owner decisions, 2026-09-22** (from the first share-site review):
1. Health facts carry **two judgements, not one** — an absolute grade against a printed rule *and*
   the same number against the company's own filed history. They answer different questions ("is
   this good?" vs "is this normal for them?"), and the second reads a utility or a REIT fairly when
   the first says D.
2. **Theme is independent of layout** — Light / Dark / System, either layout on either ground —
   and the longer goal is a **custom accent picker** so the user chooses their own colors, matching
   the owner's other apps.
3. **Keep the model toggle as it is** (one model at a time).
4. **Price vs value leads the screen**, business health below it.

- [x] Health facts are **grades with the threshold printed next to them** (`GradedFactRow`, both apps).
- [x] Each fact also carries a **historical read** from the 10-K series plus a sparkline of the
      underlying figure — fundamentals by filed year, never price.
      - Compare like with like: revenue *growth* is judged 5-yr rate vs full-period rate, never
        revenue *level* — the level reads "best in 10 years" for any healthy company and says nothing.
      - **Under five filed years, show no historical read at all.** A trend drawn from two years
        (CRWV) is not a trend.
      - **Debt load gets no historical read**: the annual series carries `equity` but not
        `total_debt`, so there is nothing honest to compare. See Track F.
- [x] **The thresholds live in the core.** Implemented as a *separate* `Grading` object and one new
      facade call, `reportCardJson(reportJson, lens)`, rather than new fields on `Explain.Fact`:
      the classic layout reads `Explain.healthFacts`, and adding fields there would have let the
      report card move the classic layout's output. `Explain.kt` has no diff this sprint.
- [x] ~~The thresholds are unapproved~~ — **resolved 2026-09-23**: an **investor lens**, Value
      (default) or Growth, asked once at onboarding and switchable in Settings or from a chip on the
      company screen. It changes only how a fact is graded and which fact is read first; it never
      changes a valuation, a margin of safety or a verdict.
- [x] ~~Sector-aware thresholds are an open question~~ — **resolved 2026-09-23**: per-sector scales
      where the measure means something, and a refusal with a stated reason where it does not.
- [x] The grading matrix is **curated data** in `GradingRules.kt` with its own `VERSION`.
      `GradingTest` pins the owner-reviewed grades for all eleven prototype filers, so changing a
      threshold without review fails the build (mutation-tested: moving the REIT C cut from 4% to
      6% fails it).
- [x] The active lens is **visible and switchable on the screen it affects** (the lens chip), and
      the rule is printed on every row. Switching it re-grades without re-running the valuation.
- [x] ~~Financials end up with one graded fact of four~~ — **resolved 2026-09-23** (ISSUES #84):
      profitability is **substituted** for financials (return on equity, not return on capital), so a
      bank shows two graded facts and two explained refusals, and AGNC is no longer wholly blank. Its
      trend line is derived from net income ÷ equity, both filed.
- [x] The lens boundary is **confirmed**: grading and reading order only. It never changes a
      valuation, a verdict, or which model opens first.
- [x] An **ungradable** state (`state: "ungradable"`): MCD's debt load shows no grade and says why.
- [x] An **all-four-blank** state (`blankNote`). With the ROE substitution no sample filer reaches it
      any more — AGNC now grades one fact — but the state exists and is designed.
- [x] A missing number arrives from the core as `value: null` and renders as a dash with no unit;
      tested.
- [x] Display face over body face, price/value bar, one-segment-per-check strip, provenance marks.
      **Font caveat**: the display face is SF Pro condensed on iOS and the system sans at heavy
      weight on Android, not the prototype's Bricolage Grotesque / Public Sans. Bundling those
      needs the font files downloaded and shipped — an owner decision, not taken yet.
- [x] Android parity, verified on the emulator with live data (PG: B / C / B / D), in both Expert
      Mode and basic. Also extracted Android's shared `ExpertBody`, which Track A had left inside
      `ClassicLayout` — without it, Expert Mode in the report card would have nested two scrolling
      columns, which Compose rejects at runtime.

### D. The toggles
- [x] Settings: layout picker beside Expert Mode. Each preview **is the real layout**, drawn on the
      bundled Apple sample and scaled down, so it follows the user's theme, accent and lens rather
      than being an illustration of them. Both platforms.
- [x] Settings: investor lens (Value / Growth) — its descriptions come from the core via a new
      `lensesJson()` / `Grading.lenses()`, so Swift restates none of the copy. Theme and accent
      shipped in Track B.
- [x] Onboarding gains "what kind of investor are you?" between the SEC identity step and the
      guided valuation, on both platforms. It says plainly that it changes no valuation, and its
      example quotes the live revenue-growth rules (`LensInfo.growthRule`) so it cannot drift.
- [x] Default layout — **the report card, by owner decision on 2026-09-24**. Enforced by a test on
      each platform; a stored choice of Classic is kept.
- [x] One typeface app-wide (SF Pro Rounded on iOS; Android's system face), by owner decision.
- [x] Company names in first-letter capitals (ISSUES #85), by owner decision; filed name kept as
      evidence in the dossier and Expert Mode.

### E. The iteration loop
- [x] Each design revision went to the **tester share site** (throwaway HTML mirror, not in the
      repo) so it can be judged on a real iPhone rather than in a simulator, then a round of
      questions and owner feedback before the next revision. Repeat until the design is settled —
      **then** it gets built in SwiftUI. No Swift work starts on the report-card body until the
      design is signed off.

### F. Data the design needs that the core doesn't produce yet
Small, additive, and the only engine work this sprint. Python first, mirror in Kotlin, regenerate
the oracle (non-negotiable 3).
- [ ] Add `total_debt` to the annual history series so the debt-load fact can carry a historical
      read like the other three. Purely additive: a new field on an existing record.
- [x] Delivered as `Grading` + `reportCardJson` (Track C) — see there for why not on `Explain.Fact`.

### Out of scope
Model changes, normalization, tag-map edits, technical analysis (permanently, blueprint §1), and
direction B. Sprint 4 Track B (release readiness) is still open and unaffected.

