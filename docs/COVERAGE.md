# Concept-map coverage

Generated 2026-09-22 by `scripts/coverage_probe.py` over 77 tickers (`scripts/universe.txt`), sector-aware. Regenerate after any change to the tag map; the weekly workflow fails if a company loses a concept it previously resolved.

**77 valued · 0 not valued · 0 regressions**

## Most frequently unresolved concepts

| Concept | Companies | Critical |
|---|---|---|
| `capex` | 5 |  |
| `eps_diluted` | 2 |  |
| `shares_diluted` | 2 |  |
| `pretax_income` | 2 |  |
| `revenue` | 1 | yes |
| `operating_income` | 1 |  |
| `income_tax` | 1 |  |
| `d_and_a` | 1 |  |

## Critical gaps (a model input is unavailable)

- **AGNC** (AGNC INVESTMENT CORP., CIK 1423689, reit) — `revenue` missing; no related tags

## Not valued

None.

---

A similar tag name is not the same meaning. Before mapping anything here, read `docs/DATA_VERIFICATION.md` and update **both** `Concepts.kt` and `tags.py`, then regenerate the oracle.
