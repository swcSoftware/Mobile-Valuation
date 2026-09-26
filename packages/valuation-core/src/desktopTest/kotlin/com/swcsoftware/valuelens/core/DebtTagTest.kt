package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ISSUES #92: Realty Income tags no `LongTermDebt*`; its $25B of notes are `NotesPayable`, and Oracle's
 * long-term borrowings are `LongTermNotesAndLoans`. Both read as short-term debt only, so O graded A
 * and ORCL showed 0.11× debt to equity against a true ~1.9×. Same fixture as the Python test.
 */
class DebtTagTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun normalize(t: String, cik: Long) =
        Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t)))
    private fun debtCheck(fin: NormalizedFinancials) =
        DataChecks.run(fin, null, false, AssumptionsCore(aaaYieldPct = 5.94, treasury10yPct = 4.94), null, false, 1789900000000L)
            .checks.first { it.key == "debt_coverage" }

    @Test fun notesPayableIsReadAsLongTermDebt() {
        val ttm = normalize("O", 726728).ttm!!
        assertEquals("NotesPayable", ttm.values["long_term_debt"]!!.tag)
        assertTrue(ttm.get("debt_to_equity")!! in 0.6..0.75, "${ttm.get("debt_to_equity")}")
    }

    @Test fun filersWithLongTermDebtTagsKeepThem() {
        for ((t, cik) in listOf("AAPL" to 320193L, "KO" to 21344L, "JNJ" to 200406L)) {
            val fin = normalize(t, cik)
            (fin.annual + listOfNotNull(fin.ttm)).forEach { p ->
                p.values["long_term_debt"]?.let { assertTrue(it.tag.startsWith("LongTermDebt"), "$t ${p.label}: ${it.tag}") }
            }
            assertEquals("pass", debtCheck(fin).status, t)
        }
    }

    @Test fun shortTermDebtAloneIsAWarningNotAPass() {
        // Deere today (its long-term borrowings aren't in companyfacts), and O before this fix.
        val fin = normalize("O", 726728)
        assertEquals("pass", debtCheck(fin).status)
        fin.ttm!!.values.remove("long_term_debt")
        val check = debtCheck(fin)
        assertEquals("warn", check.status)
        assertTrue(check.message.contains("Only short-term debt"), check.message)
    }
}
