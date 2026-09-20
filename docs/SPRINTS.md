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
