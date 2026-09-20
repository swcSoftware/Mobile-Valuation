package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.RateOverrides
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fake network: serves the recorded SEC fixtures, a synthetic Yahoo chart, and a rates.json. */
class FakeFetcher(private val fixtures: File, private val betaFactor: Double = 1.5, var ratesJson: String? = null, var offline: Boolean = false) : Fetcher {
    val calls = mutableListOf<String>()
    override fun get(url: String, headers: Map<String, String>): FetchResult {
        calls += url
        if (offline) return FetchResult(0, null)
        return try { FetchResult(200, body(url)) } catch (e: FetchException) { FetchResult(e.status, null) }
    }
    private fun body(url: String): String {
        return when {
            url.endsWith("company_tickers.json") -> File(fixtures, "company_tickers.json").readText()
            "companyfacts/CIK" in url -> {
                val cik = url.substringAfter("CIK").substringBefore(".json").toLong()
                val t = mapOf(320193L to "AAPL", 21344L to "KO", 200406L to "JNJ")[cik] ?: throw FetchException(404, "no facts")
                File(fixtures, "companyfacts_$t.json").readText()
            }
            "finance/chart" in url && "range=1d" in url -> """{"chart":{"result":[{"meta":{"regularMarketPrice":300.0,"currency":"USD","regularMarketTime":1789761602}}]}}"""
            "finance/chart" in url && "range=5y" in url -> syntheticChart(if ("%5EGSPC" in url || "^GSPC" in url) 1.0 else betaFactor)
            url.endsWith("rates.json") -> ratesJson ?: throw FetchException(404, "no rates")
            url.endsWith("tickers.json") -> throw FetchException(404, "no mirror")
            else -> throw FetchException(404, url)
        }
    }
    /** 60 monthly closes; the index moves ±3% alternating, the stock moves factor× that. */
    private fun syntheticChart(factor: Double): String {
        var price = 100.0
        val ts = mutableListOf<Long>(); val adj = mutableListOf<Double>()
        val start = 1600000000L
        for (i in 0 until 60) {
            val r = (if (i % 2 == 0) 0.03 else -0.02) * factor + (if (i % 5 == 0) 0.01 else 0.0) * factor
            price *= (1 + r); ts += start + i * 2629746L; adj += price
        }
        return """{"chart":{"result":[{"timestamp":[${ts.joinToString(",")}],"indicators":{"adjclose":[{"adjclose":[${adj.joinToString(",")}]}]}}]}}"""
    }
}

class MemCache : KeyValueCache {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

class CoreFacadeTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private val now = 1789900000000L  // 2026-09-20
    private val ua = "Test Person test@example.com"
    private val rates = """{"as_of":"2026-09-17","published_at":"2026-09-18T11:00:00Z","aaa_yield_pct":5.94,"treasury_10y_pct":4.94}"""

    private fun core(f: FakeFetcher, cache: MemCache = MemCache()) = ValuationCore(f, cache, Clock { now })

    @Test fun betaIsMeasuredFromPriceHistory() {
        val m = Market(FakeFetcher(fixtures, betaFactor = 1.5), MemCache(), Clock { now })
        val b = assertNotNull(m.beta("AAPL"))
        assertTrue(abs(b.beta - 1.5) < 1e-9, "beta ${b.beta}")
        assertTrue(b.r2 > 0.999)
        assertEquals(59, b.months)
    }

    @Test fun fullValuationWithMeasuredBetaAndPublishedRates() {
        val f = FakeFetcher(fixtures, betaFactor = 1.2, ratesJson = rates)
        val r = core(f).valuation("aapl", ua)
        assertEquals("AAPL", r.company.ticker)
        assertEquals(300.0, r.quote!!.price)
        assertTrue(abs(r.assumptions.beta - 1.2) < 1e-9)
        assertEquals(5.94, r.assumptions.aaaYieldPct); assertEquals(4.94, r.assumptions.treasury10yPct)
        assertTrue(r.assumptions.rateSource.startsWith("FRED"))
        assertEquals("measured", r.provenance["beta"]); assertEquals("fred", r.provenance["rates"]); assertEquals("market", r.provenance["price"])
        assertTrue(r.provenance["beta_detail"]!!.contains("R²"))
        val byKey = r.dataChecks.associateBy { it.key }
        assertEquals("pass", byKey["beta"]!!.status)
        assertEquals("pass", byKey["balance_sheet"]!!.status)
        assertEquals("pass", byKey["eps_consistency"]!!.status)
        assertEquals("pass", byKey["share_count"]!!.status)
        assertEquals("pass", byKey["signs"]!!.status)
        assertTrue(r.dataChecks.none { it.status == "fail" }, r.dataChecks.filter { it.status == "fail" }.joinToString())
        assertNotNull(r.modelA.intrinsicValuePerShare); assertNotNull(r.modelB.intrinsicValuePerShare)
    }

    @Test fun assumedBetaAndDefaultRatesAreFlagged() {
        val f = FakeFetcher(fixtures, ratesJson = null)
        val c = ValuationCore(object : Fetcher { override fun get(url: String, headers: Map<String, String>): FetchResult = if ("range=5y" in url) FetchResult(500, null) else f.get(url, headers) }, MemCache(), Clock { now })
        val r = c.valuation("KO", ua)
        assertEquals(1.0, r.assumptions.beta)
        assertEquals("assumed", r.provenance["beta"]); assertEquals("assumed", r.provenance["rates"])
        assertEquals("warn", r.dataChecks.first { it.key == "beta" }.status)
        assertEquals("warn", r.dataChecks.first { it.key == "rates" }.status)
        assertEquals("defaults", r.assumptions.rateSource)
    }

    @Test fun overridesWinOverMeasuredAndPublished() {
        val f = FakeFetcher(fixtures, ratesJson = rates)
        val r = core(f).valuation("KO", ua, priceOverride = 50.0, overrides = RateOverrides(beta = 0.8, aaaYieldPct = 6.5))
        assertEquals(0.8, r.assumptions.beta); assertEquals(6.5, r.assumptions.aaaYieldPct); assertEquals(4.94, r.assumptions.treasury10yPct)
        assertEquals("override", r.provenance["beta"]); assertEquals("override", r.provenance["rates"]); assertEquals("manual", r.provenance["price"])
        assertEquals("manual", r.quote!!.source); assertEquals(50.0, r.modelA.marginOfSafety.marketPrice)
    }

    @Test fun cachingAvoidsRepeatSecCalls() {
        val f = FakeFetcher(fixtures, ratesJson = rates); val cache = MemCache()
        val c = core(f, cache)
        c.valuation("JNJ", ua); val n = f.calls.count { "sec.gov" in it }
        c.valuation("JNJ", ua)
        assertEquals(n, f.calls.count { "sec.gov" in it })
    }

    @Test fun errorsMapToTypedExceptions() {
        val f = FakeFetcher(fixtures, ratesJson = rates)
        assertFailsWith<EngineException.UnknownTicker> { core(f).valuation("ZZZZ", ua) }
        val off = FakeFetcher(fixtures, offline = true)
        assertFailsWith<EngineException.Offline> { core(off).valuation("AAPL", ua) }
    }

    @Test fun searchAndJson() {
        val f = FakeFetcher(fixtures, ratesJson = rates)
        val c = core(f)
        assertEquals("AAPL", c.search("app", ua).first().ticker)
        val js = c.valuationJson("KO", ua, overridesJson = """{"hurdleRatePct":8.0}""")
        assertTrue(js.contains("\"model_a\"") && js.contains("\"data_checks\"") && js.contains("\"hurdle_rate_pct\":8.0"))
    }

    @Test fun ratesStaleness() {
        val s = RatesSnapshot.parse(rates)!!
        assertEquals(3, s.ageDays(now))
        assertNull(RatesSnapshot.parse("garbage"))
    }
}
