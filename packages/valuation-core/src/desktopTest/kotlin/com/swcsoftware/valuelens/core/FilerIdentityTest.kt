package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.EngineException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Successor-issuer fallback. "NEWCO" (CIK 999) is a fresh holding company with no facts; its
 * predecessor is the AAPL fixture under CIK 320193. Ordinary tickers must never hit this path.
 */
class FilerIdentityTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private val now = 1789900000000L  // 2026-09-20
    private val ua = "Test Person test@example.com"

    private fun submissions(cik: Long, name: String, sic: String, forms: List<Pair<String, String>>, tickers: List<String>) =
        """{"cik":"$cik","name":"$name","sic":"$sic","tickers":[${tickers.joinToString(",") { "\"$it\"" }}],"filings":{"recent":{"form":[${forms.joinToString(",") { "\"${it.first}\"" }}],"filingDate":[${forms.joinToString(",") { "\"${it.second}\"" }}]}}}"""

    /** Wraps the fixture fetcher with a successor scenario. `successorForms` defaults to a real 12g-3 succession. */
    private inner class Scenario(val predecessorSic: String = "3571", val predecessor10K: String = "2026-02-15",
                                 val successorForms: List<Pair<String, String>> = listOf("8-K12B" to "2026-07-01", "10-Q" to "2026-08-01")) : Fetcher {
        val inner = FakeFetcher(fixtures, ratesJson = """{"as_of":"2026-09-17","aaa_yield_pct":5.94,"treasury_10y_pct":4.94}""")
        val calls get() = inner.calls
        override fun get(url: String, headers: Map<String, String>): FetchResult {
            inner.calls += url
            return when {
                url.endsWith("company_tickers.json") -> FetchResult(200, """{"0":{"cik_str":999,"ticker":"NEWCO","title":"Apple Holdings Corp"},"1":{"cik_str":320193,"ticker":"AAPL","title":"Apple Inc."},"2":{"cik_str":21344,"ticker":"KO","title":"COCA COLA CO"}}""")
                url.contains("companyfacts/CIK0000000999") -> FetchResult(200, """{"cik":999,"entityName":"Apple Holdings Corp","facts":{}}""")
                url.contains("submissions/CIK0000000999") -> FetchResult(200, submissions(999, "Apple Holdings Corp", "3571", successorForms, listOf("NEWCO")))
                url.contains("submissions/CIK0000320193") -> FetchResult(200, submissions(320193, "APPLE INC", predecessorSic, listOf("10-K" to predecessor10K, "10-Q" to "2026-05-01"), emptyList()))
                url.contains("submissions/CIK0000021344") -> FetchResult(200, submissions(21344, "COCA COLA CO", "2080", listOf("10-K" to "2026-02-20"), listOf("KO")))
                url.contains("efts.sec.gov") -> FetchResult(200, """{"hits":{"hits":[{"_id":"999","_source":{"entity":"Apple Holdings Corp (NEWCO)"}},{"_id":"21344","_source":{"entity":"COCA COLA CO"}},{"_id":"320193","_source":{"entity":"APPLE INC"}}]}}""")
                else -> inner.get(url, headers)
            }
        }
    }

    @Test fun successorFallsBackToPredecessorWithEvidence() {
        val sc = Scenario()
        val r = ValuationCore(sc, MemCache(), Clock { now }).valuation("NEWCO", ua)
        assertEquals("NEWCO", r.company.ticker)
        assertEquals(320193, r.company.cik)             // predecessor's filings
        assertEquals("APPLE INC", r.company.name)
        assertEquals(10, r.history.size)
        assertNotNull(r.modelA.intrinsicValuePerShare)
        assertEquals("predecessor", r.provenance["filer"])
        val check = r.dataChecks.first { it.key == "filer_identity" }
        assertEquals("warn", check.status)
        assertTrue(check.message.contains("8-K12B") && check.message.contains("320193"))
        assertTrue(r.warnings.first().startsWith("Filings come from predecessor APPLE INC"))
        // KO was ranked higher in the search but has a different SIC → correctly skipped
        assertTrue(sc.calls.any { it.contains("submissions/CIK0000021344") })
    }

    @Test fun differentSicIsRejected() {
        val sc = Scenario(predecessorSic = "2080")
        assertFailsWith<EngineException.NoAnnualData> { ValuationCore(sc, MemCache(), Clock { now }).valuation("NEWCO", ua) }
    }

    @Test fun stalePredecessorIsRejected() {
        val sc = Scenario(predecessor10K = "2023-02-15")
        assertFailsWith<EngineException.NoAnnualData> { ValuationCore(sc, MemCache(), Clock { now }).valuation("NEWCO", ua) }
    }

    @Test fun ipoWithTemptingSameIndustryNamesakeIsNotSubstituted() {
        // "Apple Holdings Corp" IPO'd via S-1: same SIC as Apple Inc, same first name token, but NO 8-K12B.
        val sc = Scenario(successorForms = listOf("S-1" to "2026-06-01", "424B4" to "2026-06-20", "10-Q" to "2026-08-01"))
        val e = assertFailsWith<EngineException.NoAnnualData> { ValuationCore(sc, MemCache(), Clock { now }).valuation("NEWCO", ua) }
        assertTrue(e.message!!.contains("listed recently"), e.message)
        assertFalse(sc.calls.any { it.contains("efts.sec.gov") }, "no predecessor search without a successor notice")
    }

    @Test fun foreignFilerGetsAnHonestReason() {
        val sc = Scenario(successorForms = listOf("20-F" to "2026-04-01", "6-K" to "2026-08-01"))
        val e = assertFailsWith<EngineException.NoAnnualData> { ValuationCore(sc, MemCache(), Clock { now }).valuation("NEWCO", ua) }
        assertTrue(e.message!!.contains("foreign private issuer"), e.message)
    }

    @Test fun ordinaryTickersNeverTouchTheFallback() {
        val sc = Scenario()
        val r = ValuationCore(sc, MemCache(), Clock { now }).valuation("AAPL", ua)
        assertEquals(320193, r.company.cik)
        assertEquals("sec", r.provenance["filer"])
        assertEquals("pass", r.dataChecks.first { it.key == "filer_identity" }.status)
        assertFalse(sc.calls.any { it.contains("submissions/") || it.contains("efts.sec.gov") }, "no identity lookups for a normal filer")
    }

    @Test fun profileParsingAndTokens() {
        val p = FilerIdentity.parseProfile(submissions(1, "The Coca-Cola Co", "2080", listOf("10-K" to "2026-02-20", "8-K12B" to "2026-03-01"), listOf("KO")))!!
        assertEquals("2026-02-20", p.latest10K.toString()); assertTrue(p.hasSuccessorNotice); assertEquals("2026-02-20", p.firstFiling.toString())
        assertEquals("coca-cola", FilerIdentity.searchToken("The Coca-Cola Co"))
        assertEquals("exxonmobil", FilerIdentity.searchToken("ExxonMobil Holdings Corp"))
        assertNull(FilerIdentity.searchToken("Holdings Inc"))
    }
}
