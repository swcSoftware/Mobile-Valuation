# ValueLens — Fundamental Equity Valuation Platform

Native iOS (SwiftUI) + Android (Compose) clients sharing one Kotlin Multiplatform valuation core
that runs **entirely on the device**: it ingests SEC EDGAR 10-K / 10-Q XBRL disclosures, verifies
them (13 data checks, measured beta, no silent defaults) and produces Graham / Buffett / Munger
style intrinsic value estimates (Model A) alongside a modern DCF / ROIC fair-value view (Model B).
No server. The Python engine is the reference implementation the core is tested against.

**No technical analysis. Ever.** See [`docs/PLAN.md`](docs/PLAN.md) for the roadmap.

New here (human or agent)? Read [CLAUDE.md](CLAUDE.md) first — working rules, commands and
known tripwires — then `docs/`.

## Layout

| Path | What |
|---|---|
| `apps/ios` | SwiftUI app (iOS 17+), MVVM + Repository. Project generated with `xcodegen`. |
| `apps/android` | Kotlin / Jetpack Compose scaffold (built out in a later sprint). |
| `packages/valuation-core` | Kotlin Multiplatform core used by both apps (EDGAR, normalization, models, data checks, beta). |
| `services/valuation-engine` | Python reference implementation + oracle fixtures (development only). |
| `scripts` + `.github/workflows` | Publishes `rates.json` (FRED) and `tickers.json` to GitHub Pages daily. |
| `docs` | Plan, architecture, API contract, task board, issues, sprint log. |

## Quick start

```bash
# iOS (first build compiles the shared core via Gradle; needs a JDK 17+ — Android Studio's works)
cd apps/ios && xcodegen generate && open ValueLens.xcodeproj

# Android
cd apps/android && ./gradlew assembleDebug

# Everything else: docs/RUNBOOK.md
```

## Tests

87 automated tests across the core, both apps and the reference engine — see `docs/RUNBOOK.md` §3b.

## Branches

- `main` — production-ready. Protected; only merged on explicit owner approval.
- `staging` — simulator/device-testable builds. Pushed when work is complete and green.
- `dev` — active development. May be broken.
