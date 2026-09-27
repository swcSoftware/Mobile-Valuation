package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plain-language layer for non-expert users (Sprint 2 "Expert Mode" off). One source of copy
 * for iOS and Android. ≤ 60 words each, no formulas; the expert paragraph sits underneath.
 */
@Serializable
data class GlossaryEntry(val key: String, val term: String, val plain: String, val expert: String,
                         /** Exact words that link to this entry (see labels/glossary.json). */
                         val aliases: List<String> = emptyList())

object Explain {
    /** Moved to labels/glossary.json in Sprint 8 (owner-editable); the wording of these ten entries is unchanged. */
    val glossary: List<GlossaryEntry> get() = Glossary.entries

    fun glossary(key: String): GlossaryEntry? = glossary.firstOrNull { it.key == key }

    /** Friendly model names for basic mode. */
    fun modelName(isModelA: Boolean, expert: Boolean) = when {
        expert -> if (isModelA) "Model A · Graham · Buffett · Munger" else "Model B · DCF · WACC · ROIC"
        isModelA -> "Classic value" else -> "Cash-flow value"
    }

    fun modelBlurb(isModelA: Boolean, sectorMode: String = "general") = when (sectorMode) {
        "financial" -> if (isModelA) "Banks are valued on what they own, not what they sell: book value, and whether the return on it beats what investors demand — Graham's rule for financials." else "The modern approach for banks: how much more the bank earns on its equity than investors require, and what that excess is worth today."
        "reit" -> if (isModelA) "REITs are valued on funds from operations — earnings with depreciation added back, because buildings don't wear out like machines. Graham's formula, applied to FFO." else "The modern approach for REITs: a fair multiple of funds from operations, cross-checked against the dividend stream."
        else -> if (isModelA) "The classic approach: what the business earns for its owners, priced the way Graham and Buffett would." else "The modern approach: project the cash the business will generate and discount it back to today."
    }

    /** One sentence a first-time investor can act on. */
    fun verdictSentence(r: ValuationReport, res: ModelResult): String {
        val name = CompanyNames.display(r.company.name, r.company.ticker).trim().trimEnd('.')
        val mos = res.marginOfSafety
        val iv = mos.intrinsicValue; val p = mos.marketPrice
        if (iv == null) return "Alpha couldn't estimate a per-share value for $name from its filings yet — see the data checks below for why."
        val ivS = money(iv)
        if (p == null) return "Based on its filings, Alpha estimates $name is worth about $ivS per share. Enter a market price to see whether that's a bargain."
        val pS = money(p)
        val m = mos.marginOfSafetyPct ?: 0.0
        return when (mos.verdict) {
            "deep_value" -> "Based on its filings, $name looks worth about $ivS per share, and the market is asking only $pS — a ${m.roundToInt()}% discount. That's a wide margin of safety."
            "within_margin" -> "Based on its filings, $name looks worth about $ivS per share; the market is asking $pS, a ${m.roundToInt()}% discount. That's inside the margin of safety value investors look for."
            "below_intrinsic_thin_margin" -> "Based on its filings, $name looks worth about $ivS per share, and it trades at $pS — cheaper, but only by ${m.roundToInt()}%. Thin margin for error."
            else -> "Based on its filings, $name looks worth about $ivS per share, but the market is asking $pS — ${abs(m).roundToInt()}% more. There's no margin of safety at today's price."
        }
    }

    /** Four headline facts for basic mode: growth, profitability, debt, cash. */
    @Serializable data class Fact(val label: String, val value: String, val tone: String, val plain: String)

    fun healthFacts(r: ValuationReport): List<Fact> {
        val out = mutableListOf<Fact>()
        r.growth["revenue"]?.fiveYearCagr?.let { g ->
            val pct = g * 100
            out += Fact("Growing?", "${if (pct >= 0) "+" else ""}${pct.roundToInt()}% / yr", if (pct >= 5) "good" else if (pct >= 0) "neutral" else "bad", "Revenue has ${if (pct >= 0) "grown" else "shrunk"} about ${abs(pct).roundToInt()}% a year over the last five years.")
        }
        val mode = r.sector?.mode ?: "general"
        if (mode == "financial" || mode == "reit") {
            r.snapshot["roe"]?.value?.let { roe ->
                val pct = roe * 100
                out += Fact("Profitable?", "${pct.roundToInt()}% return on equity", if (pct >= 12) "good" else if (pct >= 7) "neutral" else "bad", "For every \$100 of shareholders' money the company earns about \$${pct.roundToInt()} a year.")
            }
        } else r.snapshot["roic"]?.value?.let { roic ->
            val pct = roic * 100
            out += Fact("Profitable?", "${pct.roundToInt()}% return on capital", if (pct >= 15) "good" else if (pct >= 8) "neutral" else "bad", "For every \$100 invested in the business it earns about \$${pct.roundToInt()} a year after tax.")
        }
        if (mode == "financial") {
            val eq = r.snapshot["equity"]?.value; val ta = r.snapshot["total_assets"]?.value
            if (eq != null && ta != null && ta > 0) { val lev = ta / eq; out += Fact("Leverage", "${fixed(lev, 1)}× assets / equity", if (lev <= 10) "good" else if (lev <= 15) "neutral" else "bad", "The balance sheet holds ${fixed(lev, 1)} dollars of assets for every dollar of equity — normal for a bank is 8–12×.") }
        } else r.snapshot["debt_to_equity"]?.value?.let { de ->
            out += Fact("Debt load", "${fixed(de, 1)}× equity", if (de <= 0.5) "good" else if (de <= 1.5) "neutral" else "bad", "The company owes ${fixed(de, 1)} dollars of debt for every dollar of shareholders' equity.")
        } ?: negativeEquityDebt(r)?.let { out += it }
        val cash = r.snapshot["cash"]?.value; val fcf = r.snapshot["fcf"]?.value
        if (cash != null && fcf != null) {
            out += Fact("Cash", "${compact(cash)} on hand · ${compact(fcf)} free cash flow", if (fcf > 0) "good" else "bad", "It holds ${compact(cash)} in cash and ${if (fcf > 0) "generated" else "burned"} ${compact(abs(fcf))} of free cash flow in the last twelve months.")
        }
        return out
    }

    /**
     * Debt load for a company whose equity is zero or negative (MCD after decades of buybacks), where
     * debt ÷ equity doesn't exist. Before ISSUES #78 the tile silently vanished; negative equity is
     * material to a value investor, so say it, and measure debt against earnings instead.
     * Never reached when debt ÷ equity exists.
     */
    fun negativeEquityDebt(r: ValuationReport): Fact? {
        val equity = r.snapshot["equity"]?.value ?: return null
        if (equity > 0) return null
        val debt = r.snapshot["total_debt"]?.value
        val lev = debtToEbitda(r)
        return if (debt != null && lev != null) Fact("Debt load", "Negative equity · ${fixed(lev, 1)}× EBITDA",
            if (lev <= 2.0) "good" else if (lev <= 3.5) "neutral" else "bad",
            "Shareholders' equity is below zero (${compact(equity)}), usually after years of buybacks, so debt measured against equity has no meaning. Measured against earnings instead, it owes ${fixed(lev, 1)} dollars of debt for every dollar of EBITDA (operating income plus depreciation) a year.")
        else Fact("Debt load", "Negative equity", "neutral",
            "Shareholders' equity is below zero (${compact(equity)}), so debt measured against equity has no meaning, and earnings aren't available to measure it against instead.")
    }

    /** Total debt ÷ EBITDA (operating income + D&A, trailing twelve months); null unless both are positive. */
    fun debtToEbitda(r: ValuationReport): Double? {
        val debt = r.snapshot["total_debt"]?.value ?: return null
        val ebitda = (r.snapshot["operating_income"]?.value ?: return null) + (r.snapshot["d_and_a"]?.value?.let { abs(it) } ?: return null)
        return if (debt > 0 && ebitda > 0) debt / ebitda else null
    }

    fun checksSummary(r: ValuationReport): String {
        val fails = r.dataChecks.count { it.status == "fail" }; val warns = r.dataChecks.count { it.status == "warn" }; val n = r.dataChecks.size
        return when {
            n == 0 -> "No data checks were run."
            fails > 0 -> "$fails of $n data checks failed — the value is hidden until the numbers can be trusted."
            warns > 0 -> "$n checks run · $warns warning${if (warns > 1) "s" else ""} worth a look."
            else -> "All $n data checks passed."
        }
    }

    // --- tiny formatters (no platform Locale) ---
    private fun fixed(v: Double, d: Int) = PyFmt.fixed(v, d)
    private fun money(v: Double) = "$" + PyFmt.commas(v, 2)
    private fun compact(v: Double): String { val a = abs(v); val s = if (v < 0) "−" else ""; return when { a >= 1e12 -> "$s$${fixed(a / 1e12, 2)}T"; a >= 1e9 -> "$s$${fixed(a / 1e9, 1)}B"; a >= 1e6 -> "$s$${fixed(a / 1e6, 1)}M"; else -> "$s$${fixed(a, 0)}" } }
}
