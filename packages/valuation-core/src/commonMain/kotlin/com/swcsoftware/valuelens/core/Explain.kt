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
data class GlossaryEntry(val key: String, val term: String, val plain: String, val expert: String)

object Explain {
    val glossary: List<GlossaryEntry> = listOf(
        GlossaryEntry("intrinsic_value", "Intrinsic value",
            "What a business is actually worth per share, judged by the cash it earns — not by what people are paying for it today. ValueLens estimates it two ways from the company's own SEC filings.",
            "Present value of the cash an owner can take out of the business over its life. Model A uses Graham's earnings multiple and Buffett's owner earnings; Model B discounts projected free cash flow to the firm at WACC."),
        GlossaryEntry("margin_of_safety", "Margin of safety",
            "The discount between the price and what the business is worth. Buying at 25–50% below intrinsic value leaves room to be wrong. If the price is above the value, there is no margin — just risk.",
            "MoS = 1 − price ÷ intrinsic value. Graham's central rule: the estimate is uncertain, so demand a price far enough below it that errors in the estimate don't lose money."),
        GlossaryEntry("owner_earnings", "Owner earnings",
            "The cash a business could hand to its owners each year after paying to keep itself running. Buffett's preferred measure of what a company really makes.",
            "Net income + depreciation & amortization − maintenance capital expenditure ± change in working capital. ValueLens proxies maintenance capex as min(capex, D&A) and labels it."),
        GlossaryEntry("free_cash_flow", "Free cash flow",
            "Cash from operations minus what was spent on equipment and buildings. It's the money left over after the business invests in itself.",
            "Cash from operating activities − capital expenditures (levered FCF). Model B uses FCFF — unlevered, after-tax operating cash flow before interest."),
        GlossaryEntry("roic", "Return on capital (ROIC)",
            "How much profit the company earns for every dollar invested in it. High and steady returns are the signature of a business with a durable advantage.",
            "NOPAT ÷ invested capital (equity + debt − cash). Compared with WACC: a positive, persistent spread indicates an economic moat."),
        GlossaryEntry("wacc", "Cost of capital (WACC)",
            "The return investors and lenders expect for putting money into this company. Future cash is discounted at this rate — the riskier the business, the higher it is and the less future cash is worth today.",
            "Weighted average of cost of equity (CAPM: risk-free + β × equity risk premium) and after-tax cost of debt, weighted by market value of equity and debt."),
        GlossaryEntry("beta", "Beta",
            "How much the stock swings compared with the overall market. 1.0 moves with the market; 0.5 is calmer; 1.5 is wilder. ValueLens measures it from five years of monthly prices — it is never just assumed.",
            "Slope of the regression of the stock's monthly returns on the S&P 500's over 5 years (≥ 36 months required). R² shows how much of the movement the market explains."),
        GlossaryEntry("book_value", "Book value",
            "What the accountants say the company owns minus what it owes. A floor of sorts — though for modern companies brands and software rarely show up in it.",
            "Shareholders' equity from the balance sheet. Per share = equity ÷ diluted shares. NNWC (net-net working capital) is Graham's harsher liquidation view."),
        GlossaryEntry("graham_number", "Graham formula",
            "A quick rule from Benjamin Graham: a company's value is its earnings per share times a multiple that grows with expected growth. Adjusted for today's interest rates.",
            "V* = EPS × (8.5 + 2g) × 4.4 ÷ Y, where g is expected growth (capped at 15%) and Y the current AAA corporate bond yield."),
        GlossaryEntry("data_checks", "Data checks",
            "Before ValueLens shows a value, it tests the numbers it pulled from SEC: do the books balance, are the figures recent, is anything assumed rather than measured? Failures block the value; warnings are shown beside it.",
            "Balance-sheet identity, EPS vs NI/shares, share-count plausibility, TTM period alignment, filing and price freshness, sign sanity, and provenance of beta, tax rate, cost of debt and rates."),
    )

    fun glossary(key: String): GlossaryEntry? = glossary.firstOrNull { it.key == key }

    /** Friendly model names for basic mode. */
    fun modelName(isModelA: Boolean, expert: Boolean) = when {
        expert -> if (isModelA) "Model A · Graham · Buffett · Munger" else "Model B · DCF · WACC · ROIC"
        isModelA -> "Classic value" else -> "Cash-flow value"
    }

    fun modelBlurb(isModelA: Boolean) = if (isModelA)
        "The classic approach: what the business earns for its owners, priced the way Graham and Buffett would."
    else "The modern approach: project the cash the business will generate and discount it back to today."

    /** One sentence a first-time investor can act on. */
    fun verdictSentence(r: ValuationReport, res: ModelResult): String {
        val name = r.company.name.trim().trimEnd('.')
        val mos = res.marginOfSafety
        val iv = mos.intrinsicValue; val p = mos.marketPrice
        if (iv == null) return "ValueLens couldn't estimate a per-share value for $name from its filings yet — see the data checks below for why."
        val ivS = money(iv)
        if (p == null) return "Based on its filings, ValueLens estimates $name is worth about $ivS per share. Enter a market price to see whether that's a bargain."
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
        r.snapshot["roic"]?.value?.let { roic ->
            val pct = roic * 100
            out += Fact("Profitable?", "${pct.roundToInt()}% return on capital", if (pct >= 15) "good" else if (pct >= 8) "neutral" else "bad", "For every \$100 invested in the business it earns about \$${pct.roundToInt()} a year after tax.")
        }
        r.snapshot["debt_to_equity"]?.value?.let { de ->
            out += Fact("Debt load", "${fixed(de, 1)}× equity", if (de <= 0.5) "good" else if (de <= 1.5) "neutral" else "bad", "The company owes ${fixed(de, 1)} dollars of debt for every dollar of shareholders' equity.")
        }
        val cash = r.snapshot["cash"]?.value; val fcf = r.snapshot["fcf"]?.value
        if (cash != null && fcf != null) {
            out += Fact("Cash", "${compact(cash)} on hand · ${compact(fcf)} free cash flow", if (fcf > 0) "good" else "bad", "It holds ${compact(cash)} in cash and ${if (fcf > 0) "generated" else "burned"} ${compact(abs(fcf))} of free cash flow in the last twelve months.")
        }
        return out
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
