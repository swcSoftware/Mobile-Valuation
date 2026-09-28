package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sprint 9: the SEC filings behind a report, and where each one's main document lives. */
class FilingsTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun fin(t: String, cik: Long) = Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t)))

    @Test fun everyFiledFigureTracesToAFilingInTheList() {
        val f = fin("AAPL", 320193)
        val used = Filings.used(f)
        assertTrue(used.isNotEmpty())
        assertEquals(used.size, used.map { it.accession }.toSet().size, "one entry per filing")
        assertTrue(used.all { it.form.startsWith("10-K") || it.form.startsWith("10-Q") })
        assertEquals(used.sortedByDescending { it.filed }, used, "newest first")
        // Every value read from a filing (not computed) points at a filing in the list, with its concept.
        val byAcc = used.associateBy { it.accession }
        for (p in f.annual + listOfNotNull(f.ttm)) for ((k, sv) in p.values) {
            if (sv.taxonomy == "valuelens") continue
            assertTrue(k in (byAcc[sv.accession]?.concepts ?: emptyList()), "${p.label} $k from ${sv.accession} is missing")
        }
        assertTrue(used.first().form.startsWith("10-Q"), "the latest quarter supplies the TTM figures")
        assertEquals(listOf("TTM"), used.first().periods)
        // A 10-K usually supplies only its comparative years: the next 10-K's restated figures win.
        val labels = f.annual.map { it.label }
        assertTrue(used.all { u -> u.periods.all { it == "TTM" || it in labels } })
        for (p in f.annual + listOfNotNull(f.ttm)) for ((_, sv) in p.values) {
            if (sv.taxonomy == "valuelens") continue
            assertTrue(p.label in byAcc.getValue(sv.accession).periods, "${p.label} missing from ${sv.accession}")
        }
    }

    @Test fun documentsResolveFromRecentAndFromTheOlderPageCoveringTheFiling() {
        val used = listOf(
            com.swcsoftware.valuelens.domain.FilingUsed("0000000001-26-000002", "10-Q", "2026-07-30", listOf("TTM"), listOf("revenue")),
            com.swcsoftware.valuelens.domain.FilingUsed("0000000001-22-000001", "10-K", "2022-02-10", listOf("FY2020", "FY2021"), listOf("revenue")),
            com.swcsoftware.valuelens.domain.FilingUsed("0000000001-19-000009", "10-K", "2019-02-10", listOf("FY2018"), listOf("revenue")),
        )
        val recent = """{"filings":{"recent":{"accessionNumber":["0000000001-26-000002"],"primaryDocument":["x-20260630.htm"],"reportDate":["2026-06-30"]},
            "files":[{"name":"CIK0000000001-submissions-001.json","filingFrom":"2021-01-01","filingTo":"2022-12-31"},
                     {"name":"CIK0000000001-submissions-002.json","filingFrom":"2020-01-01","filingTo":"2020-12-31"}]}}"""
        val requested = mutableListOf<String>()
        val docs = Filings.documents(used, 1, recent) { name ->
            requested += name
            if (name.endsWith("001.json")) """{"accessionNumber":["0000000001-22-000001"],"primaryDocument":["x-20211231.htm"]}""" else null
        }
        assertEquals("https://www.sec.gov/Archives/edgar/data/1/000000000126000002/x-20260630.htm", docs[0].documentUrl)
        assertEquals("2026-06-30", docs[0].reportDate, "the filing's own period comes from EDGAR's list")
        assertNull(docs[1].reportDate, "the older page in this test doesn't carry one")
        assertEquals("https://www.sec.gov/Archives/edgar/data/1/000000000122000001/x-20211231.htm", docs[1].documentUrl)
        assertNull(docs[2].documentUrl, "2019 isn't covered by any page: no document, but still an index link")
        assertEquals("https://www.sec.gov/Archives/edgar/data/1/000000000119000009/0000000001-19-000009-index.htm", docs[2].indexUrl)
        assertEquals(listOf("CIK0000000001-submissions-001.json"), requested, "only the page covering a missing filing is fetched")
    }
}
