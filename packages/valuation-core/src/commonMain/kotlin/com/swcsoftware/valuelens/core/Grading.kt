package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.verdictEnum
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Everything the report-card layout shows that is a *judgement* — grades, rules, historical reads,
 * the verdict chip, the lens — produced here so both apps render the same answer (CLAUDE.md
 * non-negotiable 7). A layout draws these; it never computes one.
 *
 * Deliberately separate from `Explain.healthFacts`, which the classic layout still reads unchanged:
 * the report card is an add-on, so nothing the classic layout shows may move because of it.
 *
 * Ported from the prototype the owner signed off on a phone (docs/design/preview), so the grades in
 * the app are the grades that were reviewed. `GradingTest` pins them.
 */
object Grading {

    fun reportCard(r: ValuationReport, lens: Lens): ReportCardSummary {
        val mode = r.sector?.mode ?: "general"
        val facts = GradingRules.ORDER.getValue(lens).map { slot ->
            when (slot) {
                GradingRules.Slot.PROFIT -> profitability(r, lens, mode)
                GradingRules.Slot.DEBT -> debtLoad(r, lens, mode)
                GradingRules.Slot.CONVERSION -> cashConversion(r, lens, mode)
                GradingRules.Slot.GROWTH -> revenueGrowth(r, lens, mode)
            }
        }
        val graded = facts.count { it.grade != null }
        return ReportCardSummary(
            lens = lens.name,
            lensName = lens.displayName,
            lensBlurb = lens.blurb,
            modeLabel = modeLabel(mode),
            facts = facts,
            gradedCount = graded,
            blankNote = if (graded == 0) blankNote(r, mode) else null,
            chipA = chip(r, r.modelA.marginOfSafety.verdictEnum),
            chipB = chip(r, r.modelB.marginOfSafety.verdictEnum),
            rulesVersion = GradingRules.VERSION,
        )
    }

    // ---- the four slots ---------------------------------------------------------------------

    private fun profitability(r: ValuationReport, lens: Lens, mode: String): GradedFact {
        // A bank or insurer is funded by depositors and policyholders, so return on *capital* is not
        // comparable to an operating company's; return on equity is the measure that fits.
        val onEquity = mode == "financial"
        val current = (if (onEquity) r.snapshot["roe"]?.value else r.snapshot["roic"]?.value)?.times(100)
        val series = years(r).map { h ->
            h.fiscalYear!! to if (onEquity) {
                val ni = h.netIncome; val eq = h.equity
                if (ni != null && eq != null && eq != 0.0) ni / eq * 100 else null
            } else h.roic?.times(100)
        }
        val history = historyRead(series, current, higherIsBetter = true)
        val lead = if (onEquity) {
            "What the business earns on its shareholders' own stake. A bank or insurer is funded by depositors and policyholders, so return on capital would not be comparable to an operating company's — return on equity is the measure that fits. The line is net income ÷ equity for each filed year"
        } else {
            "What the business earns on the money invested in it. The line is return on capital for each filed year"
        }
        val why = lead + (history?.let { "; over ${it.firstYear}–${it.lastYear} it ran between ${pct(it.min)} and ${pct(it.max)}, averaging ${pct(it.average)}." } ?: ".")
        return fact(GradingRules.Slot.PROFIT, if (onEquity) "Return on equity" else "Return on capital",
                    lens, mode, current, ::pct, why, history)
    }

    private fun debtLoad(r: ValuationReport, lens: Lens, mode: String): GradedFact {
        val equity = r.snapshot["equity"]?.value
        if (equity != null && equity <= 0 && GradingRules.rule(lens, mode, GradingRules.Slot.DEBT) != null) {
            // Company-specific, not sector-wide: negative equity makes the ratio meaningless for
            // *this* filer (MCD after decades of buybacks). Say so rather than print a number.
            return GradedFact(
                slot = "debt", label = "Debt load", value = null, grade = null,
                rule = "Rule does not fit this filer", state = "ungradable",
                why = "Equity is negative (${compact(equity)}), so debt measured against equity has no meaning for this company. We show nothing rather than a number that would mislead.",
                history = null,
            )
        }
        val why = "Borrowings measured against shareholders' own stake. No trend line yet: the annual series we pull from the filings carries equity but not total debt, so there is nothing honest to compare year by year."
        return fact(GradingRules.Slot.DEBT, "Debt load", lens, mode, r.snapshot["debt_to_equity"]?.value,
                    { "${fixed(it, 2)}× equity" }, why, null)
    }

    private fun cashConversion(r: ValuationReport, lens: Lens, mode: String): GradedFact {
        val fcf = r.snapshot["fcf"]?.value
        val ni = r.snapshot["net_income"]?.value
        val current = if (fcf != null && ni != null && ni != 0.0) fcf / ni else null
        val series = years(r).map { h ->
            val f = h.fcf; val n = h.netIncome
            h.fiscalYear!! to if (f != null && n != null && n != 0.0) f / n else null
        }
        val history = historyRead(series, current, higherIsBetter = true)
        val why = "How much reported profit actually arrives as free cash. The line is free cash flow ÷ net income for each filed year" +
            (history?.let { "; ${it.firstYear}–${it.lastYear} ran ${fixed(it.min, 2)}× to ${fixed(it.max, 2)}×." } ?: ".")
        return fact(GradingRules.Slot.CONVERSION, "Cash conversion", lens, mode, current,
                    { "${fixed(it, 2)}× profit" }, why, history)
    }

    private fun revenueGrowth(r: ValuationReport, lens: Lens, mode: String): GradedFact {
        val entry = r.growth["revenue"]
        val rate = entry?.fiveYearCagr?.times(100)
        val full = entry?.fullPeriodCagr?.times(100)
        val years = entry?.fullPeriodYears

        // Year-on-year growth for the shape, and the 5-year rate against the full-period rate for the
        // judgement — like with like. Revenue *level* would read "best in 10 years" for any healthy
        // company, which says nothing.
        val hist = years(r)
        val yoy = hist.zipWithNext { prev, cur ->
            val p = prev.revenue; val c = cur.revenue
            if (p != null && c != null && p != 0.0) TrendPoint(cur.fiscalYear!!, (c / p - 1) * 100) else null
        }.filterNotNull()

        val history = if (rate != null && full != null && years != null && years >= 5 && yoy.size >= 3) {
            val faster = rate >= full
            HistoryRead(
                phrase = if (faster) "Faster than its $years-yr rate" else "Slower than its $years-yr rate",
                tone = if (faster) "good" else "bad",
                points = yoy,
                min = yoy.minOf { it.value }, max = yoy.maxOf { it.value },
                average = yoy.map { it.value }.average(),
                firstYear = yoy.first().year, lastYear = yoy.last().year,
            )
        } else null

        val why = "Compound sales growth. The grade is the 5-year rate" +
            (if (rate != null && full != null && years != null) ", ${pct(rate)} against ${pct(full)} over the full $years years" else "") +
            ". The line is each year's growth on the one before" +
            (history?.let { ", ranging ${pct(it.min)} to ${pct(it.max)}." } ?: ".")
        return fact(GradingRules.Slot.GROWTH, "Revenue growth", lens, mode, rate,
                    { "${if (it >= 0) "+" else ""}${pct(it)} / yr" }, why, history,
                    missing = "No revenue line could be read from these filings — a mortgage REIT, for instance, reports net interest income instead.")
    }

    // ---- shared builder -----------------------------------------------------------------------

    /**
     * One builder for all four, so a refused measure, a missing number and a graded one are handled
     * the same way everywhere instead of four near-copies drifting apart.
     */
    private fun fact(
        slot: GradingRules.Slot, label: String, lens: Lens, mode: String,
        value: Double?, format: (Double) -> String, why: String, history: HistoryRead?,
        missing: String = "This figure is not in the filings we could read for this company.",
    ): GradedFact {
        val key = slot.name.lowercase()
        val rule = GradingRules.rule(lens, mode, slot)
            ?: return GradedFact(key, label, null, null, "Rule does not fit this filer", "refused",
                                 GradingRules.refusal(mode, slot) ?: "This measure does not describe this kind of filer.", null)
        if (value == null || !value.isFinite()) {
            // Never a unit on a missing number: "—× equity" and "— / yr" both shipped in the prototype.
            return GradedFact(key, label, null, null, "Not reported", "missing", missing, null)
        }
        return GradedFact(key, label, format(value), GradingRules.grade(value, rule), rule.text, "graded", why, history)
    }

    // ---- history -----------------------------------------------------------------------------

    private fun years(r: ValuationReport) =
        r.history.filter { it.fiscalYear != null }.sortedBy { it.fiscalYear }

    /** Fewer than five filed years is not a trend (CRWV's "faster than its 2-yr rate"), so none is drawn. */
    private const val MIN_YEARS = 5

    private fun historyRead(series: List<Pair<Int, Double?>>, current: Double?, higherIsBetter: Boolean): HistoryRead? {
        val points = series.mapNotNull { (y, v) -> v?.takeIf { it.isFinite() }?.let { TrendPoint(y, it) } }
        if (points.size < MIN_YEARS || current == null || !current.isFinite()) return null
        val values = points.map { it.value }
        val hi = values.max(); val lo = values.min(); val avg = values.average(); val n = points.size
        val best = if (higherIsBetter) hi else lo
        val worst = if (higherIsBetter) lo else hi
        fun better(a: Double, b: Double) = if (higherIsBetter) a >= b else a <= b
        val (phrase, tone) = when {
            better(current, best) -> "Best in $n years" to "good"
            better(worst, current) -> "Weakest in $n years" to "bad"
            if (higherIsBetter) current > avg else current < avg -> "Above its $n-yr average" to "good"
            else -> "Below its $n-yr average" to "bad"
        }
        return HistoryRead(phrase, tone, points, lo, hi, avg, points.first().year, points.last().year)
    }

    // ---- labels the layout must not invent -----------------------------------------------------

    private fun modeLabel(mode: String) = when (mode) {
        "financial" -> "Bank / insurer"
        "reit" -> "REIT"
        else -> "Operating company"
    }

    private fun chip(r: ValuationReport, v: Verdict): VerdictChip = when {
        r.dataChecks.any { it.status == "fail" } -> VerdictChip("Value withheld", "none")
        else -> when (v) {
            Verdict.DEEP_VALUE, Verdict.WITHIN_MARGIN -> VerdictChip("Margin of safety", "good")
            Verdict.THIN_MARGIN -> VerdictChip("Thin margin", "mid")
            Verdict.ABOVE_INTRINSIC -> VerdictChip("Above fair value", "bad")
            Verdict.INSUFFICIENT -> VerdictChip("Not valued", "none")
        }
    }

    private fun blankNote(r: ValuationReport, mode: String): String {
        val name = r.company.name.trim().removeSuffix(".")
        return "$name files as a ${modeLabel(mode).lowercase()}, and none of the four general health measures could be computed from its filings — a mortgage REIT reports no revenue line, for instance. The valuation above still uses the models built for its industry; only these facts are blank. The data notes list exactly what was missing."
    }

    // ---- formatting (no platform Locale; minus sign is typographic) ---------------------------

    private fun fixed(v: Double, d: Int) = PyFmt.fixed(v, d).replace("-", "−")
    private fun pct(v: Double) = fixed(v, 1) + "%"
    private fun compact(v: Double): String {
        val a = abs(v); val s = if (v < 0) "−" else ""
        return when {
            a >= 1e12 -> "$s$${PyFmt.fixed(a / 1e12, 2)}T"
            a >= 1e9 -> "$s$${PyFmt.fixed(a / 1e9, 1)}B"
            a >= 1e6 -> "$s$${PyFmt.fixed(a / 1e6, 1)}M"
            else -> "$s$${PyFmt.fixed(a, 0)}"
        }
    }
}

@Serializable
data class ReportCardSummary(
    /** `VALUE` or `GROWTH` — echoed back so the layout can show which lens produced these grades. */
    val lens: String,
    val lensName: String,
    val lensBlurb: String,
    /** "Operating company" / "Bank / insurer" / "REIT". */
    val modeLabel: String,
    /** Four facts, already in the lens's reading order. */
    val facts: List<GradedFact>,
    val gradedCount: Int,
    /** Present only when nothing could be graded; replaces the section rather than showing four dashes. */
    val blankNote: String? = null,
    val chipA: VerdictChip,
    val chipB: VerdictChip,
    val rulesVersion: String,
)

@Serializable
data class GradedFact(
    /** `profit` / `debt` / `conversion` / `growth`. */
    val slot: String,
    val label: String,
    /** Formatted with its unit, or null when there is nothing to show — never "—× equity". */
    val value: String?,
    /** A–F, or null when refused, ungradable or missing. */
    val grade: String?,
    /** The threshold, printed next to the grade so it can be checked; or why there is none. */
    val rule: String,
    /** `graded` / `refused` (sector-wide) / `ungradable` (this filer) / `missing`. */
    val state: String,
    /** Plain-language explanation, including the historical range when there is one. */
    val why: String,
    val history: HistoryRead?,
)

@Serializable
data class HistoryRead(
    val phrase: String,
    /** `good` / `bad`. */
    val tone: String,
    /** The series behind the sparkline, oldest first. */
    val points: List<TrendPoint>,
    val min: Double,
    val max: Double,
    val average: Double,
    val firstYear: Int,
    val lastYear: Int,
)

@Serializable
data class TrendPoint(val year: Int, val value: Double)

@Serializable
data class VerdictChip(val label: String, /** `good` / `mid` / `bad` / `none` */ val tone: String)
