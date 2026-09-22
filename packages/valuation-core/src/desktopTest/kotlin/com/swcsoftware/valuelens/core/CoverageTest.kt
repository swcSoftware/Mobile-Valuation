package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun facts(t: String, cik: Long) = CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t))

    @Test fun camelCaseSplitAndKeywords() {
        assertEquals(listOf("revenue", "from", "contract", "with", "customer"), Coverage.words("us-gaap:RevenueFromContractWithCustomer"))
        val kw = Coverage.keywords(Concepts.BY_KEY["revenue"]!!)   // stemmed
        assertTrue("revenue" in kw && "sale" in kw, kw.toString())
        assertFalse("net" in kw, "generic words must not identify a concept")
    }

    @Test fun candidatesMatchTheHeadNounNotIncidentalWords() {
        // `RevenuesNetOfInterestExpense` is a revenue tag, so "interest" and "expense" appear in the
        // concept's words — but InterestExpense is not revenue. Head-noun matching must exclude it.
        for (t in listOf("AAPL" to 320193L, "JPM" to 19617L, "KO" to 21344L)) {
            val cf = facts(t.first, t.second)
            val c = Coverage.candidates(cf, Concepts.BY_KEY["revenue"]!!)
            assertTrue(c.all { "Revenue" in it || "Sales" in it }, "${t.first}: $c")
            assertFalse(c.any { "InterestExpense" in it || "IncomeTax" in it }, "${t.first}: $c")
        }
        assertEquals("revenue", Coverage.head("us-gaap:Revenues"))
        assertEquals("interest", Coverage.head("us-gaap:InterestExpense"))
        assertEquals("payment", Coverage.head("us-gaap:PaymentsToAcquirePropertyPlantAndEquipment"))
    }

    @Test fun cleanFilerHasNoRequiredGaps() {
        val cf = facts("AAPL", 320193)
        val r = Coverage.analyze(Statements.normalize(cf), cf, "test")
        assertEquals(0, r.gaps.count { it.critical }, r.gaps.toString())
        assertTrue(r.gaps.none { it.requirement == "optional" }, "optional concepts must never be reported as gaps")
        assertEquals("AAPL", r.ticker)
    }

    @Test fun sectorDowngradesConceptsABankNeverReports() {
        val cf = facts("JPM", 19617)
        val general = Coverage.analyze(Statements.normalize(cf), cf, "test", SectorMode.GENERAL)
        val bank = Coverage.analyze(Statements.normalize(cf), cf, "test", SectorMode.FINANCIAL)
        assertTrue(general.gaps.any { it.concept == "capex" }, "a bank has no capex; in general mode that reads as a gap")
        assertTrue(bank.gaps.none { it.concept == "capex" }, "financial mode must not report capex as a gap")
        assertEquals("optional", Coverage.requirementFor(Concepts.BY_KEY["capex"]!!, SectorMode.FINANCIAL))
        assertEquals("expected", Coverage.requirementFor(Concepts.BY_KEY["capex"]!!, SectorMode.GENERAL))
    }

    @Test fun issueUrlIsPrefilledAndEncoded() {
        val cf = facts("JPM", 19617)
        val r = Coverage.analyze(Statements.normalize(cf), cf, "test 1.0", SectorMode.GENERAL)
        val url = Coverage.issueUrl(r)
        assertTrue(url.startsWith("https://github.com/swcSoftware/Mobile-Valuation/issues/new?labels=coverage&title="))
        assertTrue(url.contains("JPM") && url.contains("19617"))
        assertFalse(url.contains(" "), "spaces must be percent-encoded")
        assertFalse(url.contains("\n"))
        val body = Coverage.issueBody(r)
        assertTrue(body.contains("| CIK | 19617 |") && body.contains("Concepts.kt") && body.contains("tags.py"))
        assertTrue(Coverage.issueTitle(r).startsWith("coverage: JPM (CIK 19617)"))
    }

    @Test fun percentEncodingHandlesNonAscii() {
        val r = com.swcsoftware.valuelens.domain.CoverageReport("Ω", 1, "Böhm & Co — “quoted”", "2026-01-01", "v", "m")
        val url = Coverage.issueUrl(r)
        assertFalse(url.contains("Ω") || url.contains("&quot"))
        assertTrue(url.contains("%") && url.count { it == '&' } == 2, "only the two query separators remain literal")
    }
}
