# Sprint Log

## Sprint 0 — 2026-09-18 — Alpha skeleton

**Goal**: fully tappable iOS MVP in the simulator, backed by a real EDGAR-driven valuation engine.

**Delivered**
- `services/valuation-engine`: FastAPI service, live-tested against AAPL, KO, MSFT, JNJ, BRK-B.
- `apps/ios`: SwiftUI app, built and driven end-to-end in the iPhone 16 Pro simulator:
  identity → case study (4 steps, live AAPL) → watchlist → search JNJ → live valuation →
  Model A/B toggle → PDF dossier and 1:1 share card exported to Documents.
- `apps/android`: scaffold with contract models.
- `docs/`: this folder. Branches `main`, `staging`, `dev`.
- Tester web preview (throwaway HTML mirror with bundled data, not part of the codebase):
  https://claude.ai/artifact/VQYGXRHyyEMSPQB3swSKJj — private until shared from its Share menu.

**Bugs found and fixed during the session**
- Snake-case decoding of `treasury_10y_pct` (Swift capitalizes the `y`).
- 52/53-week fiscal years double-labelled (JNJ) → chart double-counting.
- Stale `dei` share counts for multi-class filers producing $900k/share "values".
- Stale TTM tags leaking into current-period math.
- Stooq quotes 404 → added Yahoo chart provider.
- Unsigned simulator build can't write Keychain.

**Deferred (see ISSUES.md / TASKS.md)**: multi-class shares, ΔNWC smoothing, licensed prices,
Android screens, hosted engine.

**Verification**: manual, in-simulator. Automated: 3 engine formula tests (`pytest`).

## Sprint 1 — 2026-09-19 — Any ticker, anywhere

**Goal**: all Sprint 1 sub-goals (engine hardening, iOS polish, full Android port), automated
tests green, merged to `staging`.

**Delivered**
- Engine: SQLite cache, error taxonomy, per-identity rate limiting + access log, Polygon provider,
  Dockerfile/fly.toml/LAN script, FRED verified live, 26 fixture-based tests.
- iOS: typed errors + explanatory empty states, deep links, foreground refresh, LAN engine picker,
  haptics/a11y, any-ticker case study, 10 XCTests.
- Android: complete Compose port at parity with iOS, verified live on the Pixel 10 emulator
  (onboarding with JNJ, detail, Model B toggle, PDF export, deep link), 7 JVM tests.
- Docs updated; bundled samples refreshed with FRED rates.

**Owner decisions**: hosting deferred (LAN mode); FRED key added locally; no Polygon key yet.

**Verification**: 43 automated tests (26 + 10 + 7), all passing; manual runs on iPhone 16 Pro
simulator and Pixel 10 emulator.

**Deferred**: hosted deploy + Release URL (#18), physical-device LAN check (#19), Android
share-card visual check (#22), see ISSUES #18–30.

## Sprint 2 — 2026-09-19 — On-device engine, consumer clarity, verified data

**Goal**: no hosted server; a non-expert can read a valuation; nothing reaches a formula unverified.

**Delivered**
- `packages/valuation-core` (Kotlin Multiplatform): normalization + Model A/B ported and proven
  identical to the Python reference (oracle diff, 0 differences); EDGAR client, quotes, **measured
  beta**, rates loader and a 13-check **data verification gate** — all running inside both apps.
- Android and iOS now call SEC EDGAR directly from the device with the user's identity. The HTTP
  engine client, engine URL and LAN settings are gone. Verified live: PG on Pixel 10 (β 0.38
  measured), JNJ on iPhone 16 Pro (13 checks passed).
- **Expert Mode** (default off) with a plain-language basic view, health tiles, explainers and a
  glossary; expert view with expand-all and provenance-labeled assumptions.
- GitHub Pages pipeline: `publish_rates.py`, `publish_tickers.py`, scheduled workflow. Verified
  locally with the FRED key; awaiting owner setup (secret + Pages).
- Found and fixed a silent data error: freshest-tag selection (KO's debt, PG's cash) — ISSUES #35.

**Verification**: 60 automated tests (12 core, 7 Android, 26 engine, 15 iOS), all green; manual
runs on both simulators.

**Owner actions to finish Track B**: add `FRED_API_KEY` secret, enable Pages on `gh-pages`, run
the workflow once. Until then the apps use the bundled 2026-09-17 snapshot and say so.

## Sprint 3 — 2026-09-21 — Every ticker gives an honest answer (in progress)

**Day 1**: coverage probe across 14 diverse tickers exposed three gaps (reorganized filers, banks,
REITs). Track A shipped: successor-issuer resolution (XOM → Exxon Mobil Corp) in the core and the
Python reference, with evidence rules, a visible data check, and a no-regression guard. Hardened
after review: substitution requires an 8-K12B successor notice (IPOs/spin-offs never substitute);
no-data reasons classified (new listing, foreign filer, fund); negative DCF → null.

**Track B shipped**: sector modes. Banks/insurers/brokers valued on book value + residual income,
property REITs on FFO + dividends, mortgage REITs detected and treated as financials, cost of
equity floored at rf + 4% (also in the general DCF). Sector-aware checks and copy in both apps.
Verified live on 9 financial/REIT tickers; general path oracle unchanged. Tests: 76.
Breaking-scenario review logged ISSUES #53–60.

**Track C shipped**: multi-class shares. The core reads per-class counts, tickers and EPS from the
filing's inline-XBRL instance (only when needed) and values the company once, in the searched
class's share terms. Berkshire gets its first real valuation (BRK-A ≡ BRK-B × 1,500); Visa uses all
five classes as-converted; GOOG/GOOGL agree. Tests: 85. Scenario review logged #61–66.

**Track D shipped**: working-capital normalization (Damodaran revenue-ratio method, 5-year window,
owner-approved) in both the Python reference and the core; `working_capital` one-off check; classic
Graham removed from the case study. Headline values moved for most companies — the old ones
carried single-year working-capital swings. Bundled samples and web preview regenerated. Tests: 87.

**Sprint 3 closed** with all four tracks delivered. Open issues carried: #53, #55, #56, #59–69.

## Sprint 4 — 2026-09-22 — Track A: concept-map feedback loop

**Goal**: find tag-map gaps before users do, and make every gap a reviewable artifact — without
letting the map change itself.

**Delivered**
- `Coverage` in the core: per-filer gap detection with head-noun candidate suggestions, attached to
  every valuation and surfaced as an "Unmatched line items" card in both apps.
- Requirement levels (`required`/`expected`/`optional`) on all 31 concepts in both maps, downgraded
  per sector. This also fixed the long-standing warning noise (ISSUES #4).
- Report transport by **prefilled GitHub issue URL** — no token in the binary, no server, and the
  user reads the payload before submitting. (Replaces the endpoint/API options in the plan.)
- `scripts/coverage_probe.py` + `universe.txt` (77 tickers) → `docs/COVERAGE.md` / `coverage.json`,
  with regression detection; `.github/workflows/coverage.yml` runs it Mondays.
- `ConceptMapDriftTest` asserts `Concepts.kt` and `tags.py` are identical (mutation-tested); Gradle
  now tracks the Python map and oracle fixtures as test inputs so those tests can't go stale.

**Found by the first probe** (77/77 valued, 0 regressions): `capex` tagged only in 10-Qs for LLY,
COP, VZ and PLD — owner earnings silently missing for four mega-caps (ISSUES #71); AGNC has no
revenue-family tag at all (#72). Both logged for review rather than fixed, per the agreed loop.

**Tests**: 95 (31 engine · 49 Kotlin · 15 iOS).

**Owner-reported bug, same day**: MCD showed no valuation — McDonald's tags its diluted share count
in millions while declaring the unit as `shares`, so per-share figures were off by 10^6 and two data
checks failed. Fixed by cross-checking share counts against net income ÷ EPS and rescaling only on a
clean power of 1000 (ISSUES #77). The gate behaved correctly throughout: it refused to show a wrong
number. 1 of 77 universe tickers was affected; 5 regression tests added.

**Sprint 4 Track A closed**; Track B (release readiness) remains open and unstarted. `dev` and
`staging` both at `6427ade`; `main` untouched.

## Sprint 5 — 2026-09-22 — Two layouts, one set of numbers (in progress, design phase)

**Goal**: give the app a second presentation that doesn't read like a reference book, without
touching a single number and without disturbing the layout that already works.

**No engine, model or tag-map work.** One small additive data task (Track F) and the rest is UI.

**Design phase — done so far**
- Three directions explored as phone artboards driven by real engine output for nine awkward filers
  (not AAPL alone). Owner chose **"report card"**: light ground, health facts graded against printed
  thresholds. "The Gap" (a measuring-instrument reading of the price/value gap) is kept unbuilt as a
  future direction; sources in `docs/design/directions/`.
- Two review rounds on the tester share site, each followed by owner feedback.

**Owner decisions** (recorded in full in `docs/DESIGN.md`): the new layout is an **add-on**, not a
replacement, with the dark layout staying default; health facts carry **two** judgements (an
absolute grade *and* the company's own history); **theme is independent of layout** (light/dark/
system now, a custom accent picker later); the model toggle stays as it is; price vs value leads the
screen.

**Architecture that came out of it**: three independent axes — theme, accent, layout — where a
layout never sets a color token; `price`/`value` are never accented because that pair is how the app
is read; layouts read only `ValuationReport` / `ModelResult` / `ExplainSummary` and may not compute
a number; Expert Mode stays one shared presentation. The four trust-carrying elements (withheld
value, data notes, assumed-vs-measured, sector mode) become **required components with a test**,
because two layouts means two places they can be silently dropped.

**Found by prototyping against real filers** — four design bugs that mockup data would have hidden:
revenue-growth history compared revenue *level* (reads "best in 10 years" for anyone healthy);
a "trend" drawn from two filed years (CRWV); four empty tiles for a mortgage REIT (AGNC); units
printed on missing numbers. All fixed in the prototype. New issues: **#81** (history series has no
`total_debt`, so debt load is the only fact without a trend), **#82** (fixed thresholds are unfair
across sectors — a REIT grades D on return on capital), **#83** (the all-blank filer state).

**Blocked on the owner**: the four grading thresholds are Claude's invention and are not approved,
and #82 needs a decision — sector-aware thresholds, or a tile that declines to grade. No Swift work
starts until the design is signed off.

**Tests**: 100 (31 engine · 54 Kotlin — 47 core + 7 Android app · 15 iOS), unchanged — no app
source was touched this sprint. The engine and Kotlin suites were re-run green at sprint open;
the iOS suite was not re-run, because no iOS source changed. The only code change is a test
utility: `SampleDump` now takes `VL_DUMP_TICKERS` so arbitrary filers can be dumped for design work.
