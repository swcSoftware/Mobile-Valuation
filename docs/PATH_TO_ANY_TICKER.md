# Path to "any ticker, on a real phone"

## Where we are

Today the pipeline is already ticker-agnostic: `Search` queries SEC's full company list
(~10,000 filers) and `/companies/{ticker}/valuation` builds a report for any of them from
`companyfacts`. In the simulator with the engine running on the Mac, JNJ, PG, KO, MSFT, etc.
all work live. **The bundled AAPL/KO/MSFT JSON is only the offline fallback**, used when the
engine is unreachable.

What stops this working on a physical iPhone away from the Mac is that the engine lives at
`http://127.0.0.1:8000`.

## What is needed

### 1. Host the engine (the only hard requirement)
- Add `services/valuation-engine/Dockerfile` (python:3.12-slim, `pip install .`, `uvicorn … --host 0.0.0.0`).
- Deploy to Fly.io / Railway / Cloud Run / a $5 VPS. Single instance is plenty for testers.
- Set env: `SEC_USER_AGENT` (fallback identity), `FRED_API_KEY`, `CACHE_DIR=/data` on a
  persistent volume (or swap the disk cache for Redis/SQLite).
- Put it behind HTTPS (Fly/Railway/Cloud Run give this for free). iOS ATS then needs no
  exceptions; remove `NSAllowsLocalNetworking` for Release.
- In `AppSettings.engineURL`, default to the hosted URL for Release builds; keep localhost
  for Debug.

### 2. SEC fair-access at scale
- The engine already forwards each user's identity as the SEC `User-Agent`, so the SEC sees
  real people, not one bot. Keep that.
- Add a per-instance rate limiter across all users (SEC's limit is 10 req/s per IP). The current
  limiter is process-wide, which is correct for one instance; for multiple instances use a
  shared token bucket (Redis).
- Cache `company_tickers.json` (daily) and `companyfacts` (24h) server-side — already done on disk.

### 3. Prices for arbitrary tickers
- The Yahoo chart endpoint works for any US ticker but is unofficial. Before opening to
  testers broadly, put a licensed provider (Polygon, Twelve Data, IEX-style) behind
  `PriceProvider`. It's a one-file change in `providers/prices.py`.
- The in-app price override covers gaps in the meantime.

### 4. Coverage edge cases (engine, Sprint 2)
Any ticker *resolves*; not every ticker produces a *complete* valuation:
- **Multi-class share structures** (BRK, GOOG, META, UAA): per-share data is dimensioned by
  class in XBRL and absent from `companyfacts`. Fix: use the `frames` API or fetch the filing's
  XBRL instance and read `dei:EntityCommonStockSharesOutstanding` per class.
- **Financials & insurers** (JPM, BRK's insurance ops): no `OperatingIncomeLoss`, no classified
  balance sheet (`AssetsCurrent`). Fix: sector-specific tag maps and a "bank mode" that swaps
  owner earnings for a book-value / ROE model.
- **Foreign private issuers** (20-F filers, IFRS tags): currently not matched. Fix: add `ifrs-full`
  taxonomy fallbacks or exclude with a clear message.
- **Recent IPOs / SPACs**: <3 years of 10-Ks → no CAGR, Graham g = 0. Message rather than fail.
The engine already returns `warnings[]` and `verdict: insufficient_data` for these; the iOS
error/empty states should explain *which* limitation applied (TASKS Sprint 1).

### 5. Distribution to testers
- **TestFlight** (needs an Apple Developer account, $99/yr): archive with a real team ID in
  `project.yml` (`DEVELOPMENT_TEAM`), upload, invite up to 10,000 external testers.
- Until then: the web mirror published from this session is a throwaway preview of the same
  data and screens for feedback only: https://claude.ai/artifact/VQYGXRHyyEMSPQB3swSKJj

### Effort estimate
Hosting + Release URL: half a day. Rate limiting + persistent cache: half a day. Licensed
prices: one day incl. key management. Multi-class + sector maps: 2–3 days. TestFlight setup:
half a day.
