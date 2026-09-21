package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectorTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private val now = 1789900000000L
    private val ua = "Test Person test@example.com"
    private val rates = """{"as_of":"2026-09-17","aaa_yield_pct":5.94,"treasury_10y_pct":4.94}"""
    private fun core() = ValuationCore(FakeFetcher(fixtures, betaFactor = 1.0, ratesJson = rates), MemCache(), Clock { now })

    @Test fun sicMapping() {
        assertEquals(SectorMode.FINANCIAL, Sector.modeFor("6021")); assertEquals(SectorMode.FINANCIAL, Sector.modeFor("6712")); assertEquals(SectorMode.FINANCIAL, Sector.modeFor("6331"))
        assertEquals(SectorMode.REIT, Sector.modeFor("6798")); assertEquals(SectorMode.GENERAL, Sector.modeFor("3571")); assertEquals(SectorMode.GENERAL, Sector.modeFor(null)); assertEquals(SectorMode.GENERAL, Sector.modeFor("abc"))
    }

    @Test fun bankUsesBookValueAndResidualIncome() {
        val r = core().valuation("JPM", ua)
        assertEquals("financial", r.sector?.mode)
        val a = r.modelA.metrics.map { it.key }
        assertTrue("book_value_intrinsic" in a && "justified_pb" in a && "roe" in a, a.toString())
        assertFalse("owner_earnings" in a || "nnwc" in a, "bank must not show owner earnings / NNWC")
        assertNotNull(r.modelA.intrinsicValuePerShare)
        val b = r.modelB.metrics.map { it.key }
        assertTrue("residual_income_value" in b && "roe_ke_spread" in b && "roe_persistence" in b, b.toString())
        assertFalse("wacc" in b || "dcf_perpetuity" in b)
        assertNotNull(r.modelB.intrinsicValuePerShare)
        val roe = r.modelA.metrics.first { it.key == "roe" }.value!!
        assertTrue(roe in 5.0..30.0, "JPM ROE $roe%")
        assertEquals("pass", r.dataChecks.first { it.key == "sector_mode" }.status)
        assertEquals("pass", r.dataChecks.first { it.key == "debt_coverage" }.status)     // deposits aren't "debt"
        assertTrue(r.dataChecks.none { it.status == "fail" }, r.dataChecks.filter { it.status == "fail" }.toString())
    }

    @Test fun reitUsesFfoAndDividends() {
        val r = core().valuation("O", ua)
        assertEquals("reit", r.sector?.mode)
        val a = r.modelA.metrics.associateBy { it.key }
        assertTrue("ffo" in a && "ffo_per_share" in a && "graham_ffo_value" in a, a.keys.toString())
        assertFalse("nnwc" in a || "owner_earnings" in a)
        val ffo = a["ffo"]!!.value!!; val ni = a["ffo"]!!.inputs["net_income"]!!
        assertTrue(ffo > ni, "FFO ($ffo) must exceed net income ($ni) for a REIT — depreciation is added back")
        assertNotNull(a["dividend_coverage"]?.value)
        val b = r.modelB.metrics.associateBy { it.key }
        assertNotNull(b["ffo_multiple_value"]?.value); assertNotNull(b["dividend_discount_value"]?.value)
        assertNotNull(r.modelB.intrinsicValuePerShare)
        // A REIT's FFO-based value must be well above its GAAP-earnings Graham value
        val grahamOnEps = ModelA.run(Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_O.json").readText(), CompanyRefCore("O", 726728, "O"))), AssumptionsCore(5.94, 4.94), null)
        assertTrue(r.modelA.intrinsicValuePerShare!! > (grahamOnEps.intrinsicValuePerShare ?: 0.0))
    }

    @Test fun explainAdaptsToSector() {
        val r = core().valuation("JPM", ua)
        val facts = Explain.healthFacts(r)
        assertTrue(facts.any { it.label == "Leverage" } && facts.any { it.value.contains("return on equity") }, facts.toString())
        assertTrue(Explain.modelBlurb(true, "financial").contains("book value"))
        val js = core().explainJson(core().valuationJson("O", ua))
        assertTrue(js.contains("funds from operations"))
    }
}
