package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ISSUES #53: SEC's companyfacts hadn't ingested Coca-Cola's 10-Q filed 2026-07-29, so the TTM stopped in
 * April. The filing's own XBRL fills the gap. Same fixtures and pinned note as tests/test_gapfill.py.
 */
class GapFillTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun cf(t: String, cik: Long) = CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t))
    private val instance = File(fixtures, "instance_KO_2026Q2.xml").readText()

    private fun submissions(filed: String) = """{"filings":{"recent":{"form":["8-K","10-Q"],"accessionNumber":["x","0001628280-26-050503"],
        "primaryDocument":["x.htm","ko-20260703.htm"],"filingDate":["2026-09-01","$filed"],"reportDate":["","2026-07-03"]}}}"""

    @Test fun filersThatAreNotLaggingAreReturnedUntouched() {
        val aapl = cf("AAPL", 320193)
        val (out, note) = GapFill.fill(aapl, submissions(GapFill.latestFiled(aapl).toString())) { fail("fetched ${it.instanceUrl} for a filer that isn't lagging") }
        assertSame(aapl, out)
        assertNull(note)
        assertFalse(GapFill.needsFill(aapl, null))
    }

    @Test fun koJuneQuarterIsReadFromTheFiling() {
        val ko = cf("KO", 21344)
        assertEquals("2026-04-30", GapFill.latestFiled(ko).toString())
        val (out, note) = GapFill.fill(ko, submissions("2026-07-29")) { ref ->
            assertTrue(ref.instanceUrl.endsWith("/000162828026050503/ko-20260703_htm.xml"), ref.instanceUrl)
            instance
        }
        assertEquals("SEC's companyfacts feed has not yet published the 10-Q filed 2026-07-29, period ending 2026-07-03; " +
            "108 figures were read directly from that filing's XBRL.", note)
        val before = Statements.normalize(ko)
        val fin = Statements.normalize(out)
        assertEquals("2026-04-03", before.ttm!!.periodEnd.toString())
        assertEquals("2026-07-03", fin.ttm!!.periodEnd.toString())
        val rev = fin.ttm!!.values["revenue"]!!
        assertEquals("0001628280-26-050503", rev.accession)
        assertEquals(47_941e6 + 25_852e6 - 23_664e6, rev.value)
        assertEquals(before.annual.map { p -> p.values.mapValues { it.value.value } }, fin.annual.map { p -> p.values.mapValues { it.value.value } })
    }

    @Test fun dimensionedFactsAreSkipped() {
        val filing = GapFill.latestFiling(submissions("2026-07-29"))!!
        val facts = GapFill.parseInstance(instance, filing)
        assertEquals(108, facts.size)
        assertFalse(facts.filter { it.tag == "Revenues" }.any { it.value == 2_426e6 }, "a segment's revenue leaked in")
    }
}
