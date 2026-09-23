package com.swcsoftware.valuelens.core

/**
 * The grading matrix: investor lens → sector mode → health slot → rule, or a refusal.
 *
 * **This is a judgement, not a measurement**, so it is curated data reviewed by a human, the way
 * the concept map is (CLAUDE.md non-negotiables 5 and 7). Every rule it produces is printed next
 * to the grade it produces, so a reader can check it. Change a threshold here and bump [VERSION];
 * `GradingTest` pins the owner-approved grades for the sample filers, so an unreviewed change fails.
 *
 * Owner decisions behind it (docs/DESIGN.md, 2026-09-22/23):
 *  - two lenses, Value (default) and Growth, chosen by the user; the lens changes only how a fact is
 *    graded and which fact is read first — never a valuation, a verdict or which model opens;
 *  - per-sector scales where a measure still means something, a stated refusal where it does not;
 *  - for financials, profitability is **substituted** (return on equity), not refused.
 */
internal object GradingRules {
    const val VERSION = "2026-09-23"

    enum class Slot { PROFIT, DEBT, CONVERSION, GROWTH }

    /**
     * `cuts` are the A, B, C and D thresholds, best first; anything past the last is an F.
     * `lowerIsBetter` flips the comparison (debt).
     */
    data class Rule(val cuts: List<Double>, val text: String, val lowerIsBetter: Boolean = false)

    private val GROWTH_VALUE = Rule(listOf(10.0, 6.0, 3.0, 0.0), "A at 10% a year or better")
    private val GROWTH_GROWTH = Rule(listOf(25.0, 15.0, 8.0, 0.0), "A at 25% a year or better")

    /** `null` means the measure is meaningless for that filer type: refuse, and say why. */
    private val MATRIX: Map<Lens, Map<String, Map<Slot, Rule?>>> = mapOf(
        Lens.VALUE to mapOf(
            "general" to mapOf(
                Slot.PROFIT to Rule(listOf(20.0, 15.0, 10.0, 5.0), "A at 20% or better"),
                Slot.DEBT to Rule(listOf(0.3, 0.6, 1.0, 2.0), "A at 0.30× or less", lowerIsBetter = true),
                Slot.CONVERSION to Rule(listOf(1.0, 0.8, 0.6, 0.4), "A at 1.00× or more"),
                Slot.GROWTH to GROWTH_VALUE,
            ),
            "reit" to mapOf(
                Slot.PROFIT to Rule(listOf(8.0, 6.0, 4.0, 2.0), "A at 8% or better (REIT scale)"),
                Slot.DEBT to Rule(listOf(0.8, 1.2, 1.8, 2.5), "A at 0.80× or less (REIT scale)", lowerIsBetter = true),
                Slot.CONVERSION to Rule(listOf(2.0, 1.5, 1.0, 0.5), "A at 2.00× or more (REIT scale)"),
                Slot.GROWTH to GROWTH_VALUE,
            ),
            "financial" to mapOf(
                Slot.PROFIT to Rule(listOf(15.0, 12.0, 9.0, 5.0), "A at 15% or better (return on equity)"),
                Slot.DEBT to null,
                Slot.CONVERSION to null,
                Slot.GROWTH to GROWTH_VALUE,
            ),
        ),
        Lens.GROWTH to mapOf(
            "general" to mapOf(
                Slot.PROFIT to Rule(listOf(15.0, 10.0, 5.0, 0.0), "A at 15% or better"),
                Slot.DEBT to Rule(listOf(0.5, 1.0, 2.0, 3.0), "A at 0.50× or less", lowerIsBetter = true),
                Slot.CONVERSION to Rule(listOf(0.8, 0.5, 0.2, 0.0), "A at 0.80× or more"),
                Slot.GROWTH to GROWTH_GROWTH,
            ),
            "reit" to mapOf(
                Slot.PROFIT to Rule(listOf(6.0, 4.0, 3.0, 1.0), "A at 6% or better (REIT scale)"),
                Slot.DEBT to Rule(listOf(1.0, 1.5, 2.2, 3.0), "A at 1.00× or less (REIT scale)", lowerIsBetter = true),
                Slot.CONVERSION to Rule(listOf(1.5, 1.0, 0.7, 0.3), "A at 1.50× or more (REIT scale)"),
                Slot.GROWTH to GROWTH_GROWTH,
            ),
            "financial" to mapOf(
                Slot.PROFIT to Rule(listOf(12.0, 9.0, 6.0, 3.0), "A at 12% or better (return on equity)"),
                Slot.DEBT to null,
                Slot.CONVERSION to null,
                Slot.GROWTH to GROWTH_GROWTH,
            ),
        ),
    )

    /** Why a slot is refused for a filer type — shown in place of a grade, never left blank. */
    private val REFUSALS: Map<String, Map<Slot, String>> = mapOf(
        "financial" to mapOf(
            Slot.DEBT to "A lender funded by deposits is not comparable to a company funded by borrowings, so debt against equity says nothing useful here.",
            Slot.CONVERSION to "Free cash flow is not defined for a financial the way it is for an operating company, so there is no conversion ratio to grade.",
        ),
    )

    /** The lens also sets reading order, so switching to Growth reads as "cares about different things", not "grades easier". */
    val ORDER: Map<Lens, List<Slot>> = mapOf(
        Lens.VALUE to listOf(Slot.PROFIT, Slot.DEBT, Slot.CONVERSION, Slot.GROWTH),
        Lens.GROWTH to listOf(Slot.GROWTH, Slot.PROFIT, Slot.CONVERSION, Slot.DEBT),
    )

    /** Unknown sector modes are graded as operating companies rather than silently skipped. */
    fun rule(lens: Lens, mode: String, slot: Slot): Rule? {
        val byMode = MATRIX.getValue(lens)
        return (byMode[mode] ?: byMode.getValue("general"))[slot]
    }

    fun refusal(mode: String, slot: Slot): String? = REFUSALS[mode]?.get(slot)

    fun grade(value: Double, rule: Rule): String {
        val letters = listOf("A", "B", "C", "D")
        rule.cuts.forEachIndexed { i, cut ->
            if (if (rule.lowerIsBetter) value <= cut else value >= cut) return letters[i]
        }
        return "F"
    }
}

/** The investor lens. Asked once at onboarding, switchable after; it never touches a valuation. */
enum class Lens(val displayName: String, val blurb: String) {
    VALUE("Value", "Graham and Buffett: durable returns on capital, low debt, profit that shows up as cash. Steady growth counts for more than fast growth."),
    GROWTH("Growth", "Expansion first: reinvestment and scale, with more tolerance for debt and for cash that has not arrived yet.");

    companion object {
        /** Anything unrecognised falls back to Value — the app's default and its founding lens. */
        fun from(raw: String?): Lens = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VALUE
    }
}
