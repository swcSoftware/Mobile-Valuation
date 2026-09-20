package com.swcsoftware.valuelens

import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.Metric
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.SecIdentity
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.metric
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EngineContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun sample(t: String): ValuationReport = json.decodeFromString(File("src/main/assets/$t.json").readText())

    @Test fun bundledSamplesDecode() {
        for (t in listOf("AAPL", "KO", "MSFT")) {
            val r = sample(t)
            assertEquals(t, r.company.ticker)
            assertEquals(10, r.history.size)
            assertNotNull(r.modelA.metric("graham_revised"))
            assertNotNull(r.modelB.metric("wacc"))
            assertTrue(r.assumptions.treasury10yPct > 0)
            assertTrue(r.modelA.marginOfSafety.verdictEnum != Verdict.INSUFFICIENT)
        }
    }

    @Test fun metricCarriesFormulaAndSources() {
        val m = sample("AAPL").modelA.metric("graham_classic")!!
        assertTrue(m.formula.contains("8.5"))
        assertEquals("us-gaap:EarningsPerShareDiluted", m.sources.first().tag)
        assertNotNull(m.inputs["eps_ttm"])
    }

    @Test fun errorEnvelopeMapsToTypedExceptions() {
        val e = EngineException.from(404, """{"error":{"code":"unknown_ticker","message":"nope"},"detail":"nope"}""", json)
        assertTrue(e is EngineException.UnknownTicker); assertEquals("nope", e.message)
        assertTrue(EngineException.from(429, """{"error":{"code":"rate_limited","message":"slow"}}""", json) is EngineException.RateLimited)
        assertTrue(EngineException.from(404, """{"detail":"legacy"}""", json) is EngineException.UnknownTicker)
        assertTrue(EngineException.from(500, "", json) is EngineException.Server)
    }

    @Test fun formatters() {
        assertEquals("$416.2B", Fmt.compact(416_161_000_000.0))
        assertEquals("$1.25T", Fmt.compact(1_250_000_000_000.0))
        assertEquals("−$184.3B", Fmt.compact(-184_252_500_000.0))
        assertEquals("—", Fmt.compact(null))
        assertEquals("$72.10", Fmt.metric(Metric("k", "l", 72.1, "USD/share", "")))
        assertEquals("9.2%", Fmt.metric(Metric("k", "l", 9.15, "%", "")))
        assertEquals("10×", Fmt.metric(Metric("k", "l", 10.0, "x", "")))
    }

    @Test fun overridesOnlySendSetValues() {
        assertTrue(RateOverrides.NONE.queryParams().isEmpty())
        val q = RateOverrides(hurdleRatePct = 8.0, beta = 1.2).queryParams()
        assertEquals("8.0", q["hurdle_rate_pct"]); assertEquals("1.2", q["beta"]); assertFalse(q.containsKey("aaa_yield_pct"))
    }

    @Test fun identityValidation() {
        assertFalse(SecIdentity("Will", "w@x.com").isValid)
        assertFalse(SecIdentity("Will Tester", "nope").isValid)
        val id = SecIdentity(" Will Tester ", "will@example.com ")
        assertTrue(id.isValid); assertEquals("Will Tester will@example.com", id.userAgent)
    }

    @Test fun verdictRanking() {
        assertTrue(Verdict.DEEP_VALUE.rank < Verdict.WITHIN_MARGIN.rank)
        assertEquals(Verdict.INSUFFICIENT, Verdict.from("garbage"))
    }
}
