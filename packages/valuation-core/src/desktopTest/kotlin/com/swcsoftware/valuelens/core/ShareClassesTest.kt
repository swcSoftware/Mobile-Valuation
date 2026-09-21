package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareClassesTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private val brkRef = InstanceRef(1067983, "0001193125-26-341032", "brka-20260630.htm", "10-Q", Day.parse("2026-08-10"))
    private val vRef = InstanceRef(1403161, "0001403161-26-000104", "v-20260630.htm", "10-Q", Day.parse("2026-07-29"))

    @Test fun classTokens() {
        assertEquals("A", ShareClasses.classToken("us-gaap:CommonClassAMember"))
        assertEquals("B", ShareClasses.classToken("brka:EquivalentClassBMember"))
        assertEquals("B1", ShareClasses.classToken("v:CommonClassB1Member"))
        assertNull(ShareClasses.classToken("brka:MOnePointOneTwoFiveSeniorNotesDueTwoThousandTwentySevenMember"))
        assertNull(ShareClasses.classToken("v:A2028NotesMember"))
    }

    @Test fun instanceUrlConvention() {
        assertEquals("https://www.sec.gov/Archives/edgar/data/1067983/000119312526341032/brka-20260630_htm.xml", brkRef.instanceUrl)
    }

    @Test fun berkshireClassBSearch() {
        val r = assertNotNull(ShareClasses.resolve(File(fixtures, "instance_BRK.xml").readText(), "BRK-B", brkRef))
        assertEquals("B", r.searchedClass)
        val a = r.classes.first { it.cls == "A" }; val b = r.classes.first { it.cls == "B" }
        assertEquals("BRK-A", a.ticker); assertEquals("BRK-B", b.ticker)
        assertEquals(488450.0, a.shares); assertEquals(1408035161.0, b.shares)
        assertEquals(1.0, b.ratioToSearched)
        assertTrue(abs(a.ratioToSearched - 1500) < 2, "A→B ratio ${a.ratioToSearched}")   // 17,868 / 11.91
        // total in B-share terms ≈ 488,450 × 1500 + 1.408B ≈ 2.14B; matches the reported B-equivalent weighted average
        assertTrue(abs(r.totalInSearchedClass - 2.14e9) < 0.02e9, "total ${r.totalInSearchedClass}")
        assertNotNull(r.dilutedInSearchedClass); assertTrue(abs(r.dilutedInSearchedClass!! / r.totalInSearchedClass - 1) < 0.02)
        assertTrue(r.notes.isEmpty(), r.notes.toString())
    }

    @Test fun berkshireClassASearchGivesSameCompanyInAShares() {
        val b = ShareClasses.resolve(File(fixtures, "instance_BRK.xml").readText(), "BRK-B", brkRef)!!
        val a = ShareClasses.resolve(File(fixtures, "instance_BRK.xml").readText(), "BRK.A", brkRef)!!
        assertEquals("A", a.searchedClass)
        val msg = "A-search: ${a.classes.map { "${it.cls} eps=${it.eps} ratio=${it.ratioToSearched}" }} total=${a.totalInSearchedClass}; B-search: ${b.classes.map { "${it.cls} eps=${it.eps} ratio=${it.ratioToSearched}" }} total=${b.totalInSearchedClass}"
        assertTrue(abs(a.totalInSearchedClass * 1500 / b.totalInSearchedClass - 1) < 0.002, msg)  // same company, 1,500× fewer units
    }

    @Test fun visaExpressesEverythingInClassA() {
        val r = assertNotNull(ShareClasses.resolve(File(fixtures, "instance_V.xml").readText(), "V", vRef))
        assertEquals("A", r.searchedClass)
        assertEquals(setOf("A", "B1", "B2", "B3", "C"), r.classes.map { it.cls }.toSet())
        val c = r.classes.first { it.cls == "C" }; val b1 = r.classes.first { it.cls == "B1" }
        assertNull(c.ticker)                                           // B/C classes don't trade
        assertTrue(abs(c.ratioToSearched - 4.0) < 0.2, "C ratio ${c.ratioToSearched}")
        assertTrue(abs(b1.ratioToSearched - 1.55) < 0.1, "B1 ratio ${b1.ratioToSearched}")
        // as-converted total ≈ reported diluted weighted average (1.9B)
        assertTrue(r.totalInSearchedClass in 1.75e9..2.0e9, "total ${r.totalInSearchedClass}")
    }

    @Test fun nonTradedTickerFallsBackToClassA() {
        val r = ShareClasses.resolve(File(fixtures, "instance_V.xml").readText(), "VISA-B", vRef)!!
        assertEquals("A", r.searchedClass)
        assertTrue(r.notes.first().contains("not a listed class"))
    }

    @Test fun fullBerkshireValuationThroughTheCore() {
        // BRK facts fixture isn't recorded; reuse the KO facts (undimensioned share count stale-guarded away) with BRK's instance.
        val f = object : Fetcher {
            val inner = FakeFetcher(fixtures, ratesJson = """{"as_of":"2026-09-17","aaa_yield_pct":5.94,"treasury_10y_pct":4.94}""")
            override fun get(url: String, headers: Map<String, String>): FetchResult = when {
                url.endsWith("company_tickers.json") -> FetchResult(200, """{"0":{"cik_str":21344,"ticker":"BRK-B","title":"BERKSHIRE-LIKE CO"}}""")
                "submissions/CIK0000021344" in url -> FetchResult(200, """{"cik":"21344","name":"BERKSHIRE-LIKE CO","sic":"2080","tickers":["BRK-B"],"filings":{"recent":{"form":["10-Q","10-K"],"filingDate":["2026-08-10","2026-02-20"],"accessionNumber":["0001193125-26-341032","0001193125-26-000001"],"primaryDocument":["brka-20260630.htm","brka-20251231.htm"]}}}""")
                url.endsWith("brka-20260630_htm.xml") -> FetchResult(200, File(fixtures, "instance_BRK.xml").readText())
                else -> inner.get(url, headers)
            }
        }
        val core = ValuationCore(f, MemCache(), Clock { 1789900000000L })
        // Force the class path: KO's own cover count would normally pass, so exercise the trigger directly.
        val fin = Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_KO.json").readText(), CompanyRefCore("BRK-B", 21344, "x")))
        assertTrue(!core.needsClassResolution(fin), "KO-like filer must not trigger class resolution")
        val r = core.valuation("BRK-B", "T P t@e.com")
        assertTrue(r.shareClasses.isEmpty(), "ordinary filer: no instance fetch")
    }
}
