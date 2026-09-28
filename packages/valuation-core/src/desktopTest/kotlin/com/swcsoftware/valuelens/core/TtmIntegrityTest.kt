package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sprint 10, TTM integrity (ISSUES #97–#99). Mirrors the Python tests on the same synthetic filer; JNJ's #97
 * change is also pinned by the regenerated oracle.
 */
class TtmIntegrityTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun fin(t: String, cik: Long) = Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t)))
    private fun check(f: NormalizedFinancials, key: String) =
        DataChecks.run(f, null, false, AssumptionsCore(aaaYieldPct = 5.94, treasury10yPct = 4.94), null, false, 1_790_000_000_000L).checks.first { it.key == key }

    @Test fun aFiledTwelveMonthColumnIsTheTtm() {
        val t = fin("TTM_EDGES", 2).ttm!!
        val ni = t.values["net_income"]!!
        assertEquals(136e6, ni.value, "the filed figure, not the 135 the arithmetic would give")
        assertEquals("2026-06-30", ni.periodEnd.toString())
        assertTrue(ni.note.contains("as filed in the 10-Q"), ni.note)
        assertEquals(1300e6, t.values["revenue"]!!.value)
    }

    @Test fun computedAnnualValuesAreRebuiltNotCarried() {
        val t = fin("JNJ", 200406).ttm!!
        assertEquals("valuelens", t.values["operating_income"]!!.taxonomy)
        assertEquals(t.values["revenue"]!!.periodEnd, t.values["operating_income"]!!.periodEnd)
    }

    @Test fun laggingLinesAreRebuiltOrLabeledAndTheCheckNamesThem() {
        val f = fin("TTM_EDGES", 2)
        val t = f.ttm!!
        assertEquals(136e6 + 34e6, t.values["pretax_income"]!!.value)
        assertEquals("valuelens", t.values["pretax_income"]!!.taxonomy)
        val da = t.values["d_and_a"]!!
        assertEquals(60e6, da.value)
        assertTrue(da.note.startsWith("From the 10-K for the period ending 2025-12-31"), da.note)
        val c = check(f, "ttm_other_lines")
        assertEquals("warn", c.status)
        assertTrue(c.message.contains("Depreciation") && c.message.contains("2025-12-31"), c.message)
        assertTrue(c.message.contains("rebuilt"), c.message)
        assertEquals("pass", check(f, "ttm_alignment").status, "the three core lines are aligned: nothing withheld")
    }

    @Test fun filersWithCurrentLinesAreUntouchedAndPass() {
        for ((tk, cik) in listOf("AAPL" to 320193L, "KO" to 21344L)) {
            val f = fin(tk, cik)
            assertTrue(f.ttm!!.values.values.none { it.note.startsWith("From the ") }, tk)
            assertEquals("pass", check(f, "ttm_other_lines").status, tk)
        }
    }
}
