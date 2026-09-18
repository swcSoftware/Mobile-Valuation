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
| 12 | P3 | ios | Watchlist pull-to-refresh is sequential; 10 tickers ≈ 10 round trips. | Batch endpoint or TaskGroup. | open |
| 13 | P3 | ios | Keychain writes require a signed build; `CODE_SIGNING_ALLOWED=NO` builds silently fail to persist identity. | Documented in RUNBOOK. | documented |
| 14 | P3 | ios | ImageRenderer PDF is single-page; long histories will clip past 792pt. | Paginate in Sprint 3. | open |
| 15 | P3 | android | Scaffold has never been compiled; dependency versions are best-effort. | | open |
| 16 | P2 | engine | JNJ (and other 52/53-week filers) had two fiscal years mapped to the same label, double-counting in charts. | Fixed via `fiscal_year_for()`. | fixed |
| 17 | P3 | engine | EPS TTM is computed additively (FY + YTD − prior YTD); exact only when share count is stable. Note is shown on the metric. | | accepted |
