package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.Assumptions

/** Market inputs and model knobs; percentages in percent (5.0 == 5%). Mirrors valuation/types.py. */
class AssumptionsCore(
    val aaaYieldPct: Double, val treasury10yPct: Double, val hurdleRatePct: Double = 10.0,
    val equityRiskPremiumPct: Double = 5.0, val beta: Double = 1.0, val terminalGrowthPct: Double = 2.5,
    val exitMultiple: Double = 15.0, val taxRatePct: Double = 21.0, val projectionYears: Int = 5,
    val maxGrowthPct: Double = 15.0, val mosBandsPct: List<Double> = listOf(25.0, 50.0), val rateSource: String = "defaults",
    /** Which inputs were assumed rather than measured — surfaced by the data-check gate. */
    val betaSource: String = "assumed",
) {
    fun toDomain() = Assumptions(aaaYieldPct, treasury10yPct, hurdleRatePct, equityRiskPremiumPct, beta, terminalGrowthPct,
        exitMultiple, taxRatePct, projectionYears, maxGrowthPct, mosBandsPct, rateSource)
}
