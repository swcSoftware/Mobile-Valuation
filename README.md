# ValueLens — Fundamental Equity Valuation Platform

Native iOS (SwiftUI) + Android (Compose) clients backed by a Python valuation engine that
ingests SEC EDGAR 10-K / 10-Q XBRL disclosures and produces Graham / Buffett / Munger style
intrinsic value estimates (Model A) alongside a modern DCF / ROIC fair-value view (Model B).

**No technical analysis. Ever.** See [`docs/PLAN.md`](docs/PLAN.md) for the roadmap.

## Layout

| Path | What |
|---|---|
| `apps/ios` | SwiftUI app (iOS 17+), MVVM + Repository. Project generated with `xcodegen`. |
| `apps/android` | Kotlin / Jetpack Compose scaffold (built out in a later sprint). |
| `services/valuation-engine` | FastAPI service: EDGAR ingestion, normalization, valuation models. |
| `docs` | Plan, architecture, API contract, task board, issues, sprint log. |

## Quick start

```bash
# 1. Backend
cd services/valuation-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn valuation_engine.main:app --reload --port 8000

# 2. iOS
cd apps/ios
xcodegen generate
open ValueLens.xcodeproj   # or build via xcodebuild, see docs/RUNBOOK.md
```

## Tests

43 automated tests across the three codebases — see `docs/RUNBOOK.md` §3b.

## Branches

- `main` — production-ready. Protected; only merged on explicit owner approval.
- `staging` — simulator/device-testable builds. Pushed when work is complete and green.
- `dev` — active development. May be broken.
