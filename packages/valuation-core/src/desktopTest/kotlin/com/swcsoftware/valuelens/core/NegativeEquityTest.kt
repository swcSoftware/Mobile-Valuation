package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ISSUES #78: McDonald's equity is negative after decades of buybacks, so debt ÷ equity doesn't exist and
 * the classic layout's "Debt load" tile silently vanished. It now says "Negative equity" and measures
 * debt against EBITDA instead. Built from MCD's recorded filings, not a saved report.
 */
class NegativeEquityTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun report(t: String, cik: Long) = Report.build(
        Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t))),
        AssumptionsCore(aaaYieldPct = 5.94, treasury10yPct = 4.94), null, "2026-09-26T00:00:00Z")

    @Test fun negativeEquityShowsDebtAgainstEarnings() {
        val r = report("MCD", 63908)
        assertTrue(r.snapshot["equity"]!!.value < 0)
        assertNull(r.snapshot["debt_to_equity"])
        val lev = assertNotNull(Explain.debtToEbitda(r))
        val expected = r.snapshot["total_debt"]!!.value / (r.snapshot["operating_income"]!!.value + r.snapshot["d_and_a"]!!.value)
        assertEquals(expected, lev, 1e-9)
        assertTrue(lev in 2.0..5.0, "MCD debt/EBITDA should be a few turns, got $lev")

        val tile = Explain.healthFacts(r).first { it.label == "Debt load" }
        assertTrue(tile.value.startsWith("Negative equity · "), tile.value)
        assertTrue(tile.plain.contains("EBITDA"), tile.plain)
    }

    @Test fun positiveEquityTileIsUnchanged() {
        for ((t, cik) in listOf("AAPL" to 320193L, "KO" to 21344L, "JNJ" to 200406L)) {
            val r = report(t, cik)
            val tile = Explain.healthFacts(r).first { it.label == "Debt load" }
            assertTrue(tile.value.endsWith("× equity"), "$t: ${tile.value}")
            assertNull(Explain.negativeEquityDebt(r), t)
        }
    }

    @Test fun reportCardStaysUngradedButGivesTheMeasure() {
        val debt = Grading.reportCard(report("MCD", 63908), Lens.VALUE).facts.first { it.slot == "debt" }
        assertEquals("ungradable", debt.state)
        assertNull(debt.grade)
        assertTrue(debt.why.contains("EBITDA") && debt.why.contains("not graded"), debt.why)
    }
}
