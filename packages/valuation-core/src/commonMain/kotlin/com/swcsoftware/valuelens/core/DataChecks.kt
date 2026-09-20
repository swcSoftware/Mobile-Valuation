package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.DataCheck
import com.swcsoftware.valuelens.domain.Quote
import kotlin.math.abs

/**
 * The verification gate: every check runs before a fair value is shown. "fail" means the apps must
 * not present the intrinsic value as a number; "warn" is shown alongside it. Every model input
 * gets a provenance so nothing is silently assumed (docs/DATA_VERIFICATION.md).
 */
object DataChecks {
    class Result(val checks: List<DataCheck>, val provenance: LinkedHashMap<String, String>) {
        val hasFailure get() = checks.any { it.status == "fail" }
        val warnings get() = checks.count { it.status == "warn" }
    }

    private fun pct(a: Double, b: Double) = if (b == 0.0) Double.POSITIVE_INFINITY else abs(a - b) / abs(b)

    fun run(fin: NormalizedFinancials, quote: Quote?, priceIsManual: Boolean, a: AssumptionsCore,
            rates: RatesSnapshot?, ratesOverridden: Boolean, nowMillis: Long): Result {
        val out = mutableListOf<DataCheck>()
        val prov = LinkedHashMap<String, String>()
        val t = fin.ttm?.values
        val today = Day(floorDiv(nowMillis, Edgar.DAY))
        fun add(key: String, label: String, status: String, msg: String, vararg inputs: String) = out.add(DataCheck(key, label, status, msg, inputs.toList()))

        // 1. Balance sheet identity
        val assets = t?.get("total_assets")?.value; val lae = t?.get("liabilities_and_equity")?.value
        val liab = t?.get("total_liabilities")?.value; val eq = t?.get("equity")?.value
        val rhs = lae ?: if (liab != null && eq != null) liab + eq else null
        if (assets != null && rhs != null) {
            val d = pct(assets, rhs)
            add("balance_sheet", "Balance sheet balances", if (d <= 0.005) "pass" else if (d <= 0.05) "warn" else "fail",
                if (d <= 0.005) "Assets equal liabilities + equity (within ${PyFmt.fixed(d * 100, 2)}%)." else "Assets differ from liabilities + equity by ${PyFmt.fixed(d * 100, 1)}% — mixed filing periods or an untagged line item.", "total_assets", "total_liabilities", "equity")
        } else add("balance_sheet", "Balance sheet balances", "warn", "Could not test the balance-sheet identity (missing total assets or liabilities).", "total_assets")

        // 2. EPS consistency
        val eps = t?.get("eps_diluted")?.value; val ni = t?.get("net_income")?.value; val sh = t?.get("shares_diluted")?.value
        if (eps != null && ni != null && sh != null && sh > 0) {
            val implied = ni / sh; val d = pct(eps, implied)
            add("eps_consistency", "EPS agrees with net income ÷ shares", if (d <= 0.10) "pass" else if (d <= 0.25) "warn" else "fail",
                if (d <= 0.10) "Reported diluted EPS ${PyFmt.fixed(eps, 2)} vs implied ${PyFmt.fixed(implied, 2)}." else "Reported EPS ${PyFmt.fixed(eps, 2)} vs net income ÷ shares ${PyFmt.fixed(implied, 2)} (${PyFmt.fixed(d * 100, 0)}% apart) — possible split, restatement or share-class issue.", "eps_diluted", "net_income", "shares_diluted")
        } else add("eps_consistency", "EPS agrees with net income ÷ shares", "warn", "Diluted EPS or share count missing; Graham formulas may be unavailable.", "eps_diluted")

        // 3. Share count plausibility
        val cur = fin.currentShares?.value
        if (cur != null && sh != null && sh > 0) {
            val r = cur / sh
            add("share_count", "Share count is current and consistent", if (r in 0.7..1.3) "pass" else if (r in 0.4..2.5) "warn" else "fail",
                if (r in 0.7..1.3) "Cover-page shares ${PyFmt.commas(cur, 0)} vs diluted average ${PyFmt.commas(sh, 0)}." else "Cover-page shares ${PyFmt.commas(cur, 0)} differ from diluted average ${PyFmt.commas(sh, 0)} by ${PyFmt.fixed(r, 2)}× — per-share values may be wrong (multi-class or stale count).", "shares_outstanding", "shares_diluted")
        } else if (cur == null) add("share_count", "Share count is current and consistent", "fail", "No usable share count; per-share values cannot be computed.", "shares_outstanding")
        else add("share_count", "Share count is current and consistent", "warn", "Only one share-count source available; could not cross-check.", "shares_outstanding")

        // 4. TTM period alignment
        val ends = listOf("revenue", "net_income", "cfo").mapNotNull { t?.get(it)?.periodEnd }
        if (ends.size >= 2) {
            val spread = ends.max().epochDays - ends.min().epochDays
            add("ttm_alignment", "TTM figures come from the same period", if (spread <= 10) "pass" else if (spread <= 100) "warn" else "fail",
                if (spread <= 10) "Revenue, net income and cash flow all end ${ends.max()}." else "TTM line items end up to $spread days apart — some tags lag a filing.", "revenue", "net_income", "cfo")
        }

        // 5. Filing freshness
        fin.ttm?.periodEnd?.let { end ->
            val age = end.daysUntil(today)
            add("filing_freshness", "Latest filing is recent", if (age <= 130) "pass" else if (age <= 400) "warn" else "fail",
                if (age <= 130) "Most recent period ended $end ($age days ago)." else "Most recent period ended $end ($age days ago) — a newer 10-Q/10-K may exist or the filer stopped reporting.", "period_end")
        }

        // 6. Price freshness / provenance
        if (quote == null) { add("price", "Market price available", "warn", "No market quote; margin of safety cannot be computed until you enter a price.", "price"); prov["price"] = "none" }
        else {
            prov["price"] = if (priceIsManual) "manual" else "market"
            val d = Day.parse(quote.asOf.take(10))?.let { it.daysUntil(today) } ?: 0
            add("price", "Market price available", if (priceIsManual || d <= 5) "pass" else "warn",
                if (priceIsManual) "Using the price you entered (${PyFmt.commas(quote.price, 2)})." else if (d <= 5) "Quote from ${quote.source} as of ${quote.asOf.take(10)}." else "Quote is $d days old.", "price")
        }

        // 7. Sign sanity
        val bad = mutableListOf<String>()
        t?.get("revenue")?.value?.let { if (it <= 0) bad += "revenue ≤ 0" }
        sh?.let { if (it <= 0) bad += "shares ≤ 0" }
        t?.get("d_and_a")?.value?.let { if (it < 0) bad += "D&A < 0" }
        add("signs", "Values have the expected sign", if (bad.isEmpty()) "pass" else "fail", if (bad.isEmpty()) "Revenue, shares and D&A are positive." else bad.joinToString("; "), "revenue", "shares_diluted", "d_and_a")

        // 8. Provenance of every assumption-prone input
        prov["beta"] = a.betaSource
        if (a.betaSource == "assumed") add("beta", "Beta is measured, not assumed", "warn", "Beta ${PyFmt.fixed(a.beta, 2)} is an assumption (not enough price history to measure it). Cost of equity and the DCF depend on it.", "beta")
        else add("beta", "Beta is measured, not assumed", "pass", "Beta ${PyFmt.fixed(a.beta, 2)} measured from 5-year monthly returns vs the S&P 500.", "beta")

        prov["tax_rate"] = if (t?.containsKey("effective_tax_rate") == true) "sec" else "assumed"
        if (prov["tax_rate"] == "assumed") add("tax_rate", "Tax rate from filings", "warn", "Effective tax rate not derivable from the latest filing; using ${PyFmt.fixed(a.taxRatePct, 0)}% statutory assumption.", "tax_rate")
        else add("tax_rate", "Tax rate from filings", "pass", "Effective tax rate derived from income tax ÷ pre-tax income.", "tax_rate")

        prov["cost_of_debt"] = if (t?.containsKey("interest_expense") == true && (t["total_debt"]?.value ?: 0.0) > 0) "sec" else "assumed"
        if (prov["cost_of_debt"] == "assumed") add("cost_of_debt", "Cost of debt from filings", "warn", "Interest expense not tagged; cost of debt assumed as risk-free + 1.5%.", "cost_of_debt")
        else add("cost_of_debt", "Cost of debt from filings", "pass", "Cost of debt derived from interest expense ÷ total debt.", "cost_of_debt")

        prov["rates"] = when { ratesOverridden -> "override"; rates != null -> "fred"; else -> "assumed" }
        when {
            ratesOverridden -> add("rates", "Interest rates are current", "pass", "Using the rates you set in Settings (AAA ${PyFmt.fixed(a.aaaYieldPct, 2)}%, 10-yr ${PyFmt.fixed(a.treasury10yPct, 2)}%).", "aaa_yield", "treasury_10y")
            rates == null -> add("rates", "Interest rates are current", "warn", "Could not load published FRED rates; using defaults (AAA ${PyFmt.fixed(a.aaaYieldPct, 2)}%, 10-yr ${PyFmt.fixed(a.treasury10yPct, 2)}%).", "aaa_yield", "treasury_10y")
            else -> { val age = rates.ageDays(nowMillis); add("rates", "Interest rates are current", if (age <= Rates.STALE_AFTER_DAYS) "pass" else "warn", if (age <= Rates.STALE_AFTER_DAYS) "FRED rates as of ${rates.as_of}." else "FRED rates are $age days old (as of ${rates.as_of}); the daily publish may have stalled.", "aaa_yield", "treasury_10y") }
        }
        prov["growth"] = "sec"; prov["eps"] = if (eps != null) "sec" else "none"; prov["shares"] = fin.currentShares?.let { if (it.derived) "derived" else "sec" } ?: "none"
        return Result(out, prov)
    }
}
