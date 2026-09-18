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
