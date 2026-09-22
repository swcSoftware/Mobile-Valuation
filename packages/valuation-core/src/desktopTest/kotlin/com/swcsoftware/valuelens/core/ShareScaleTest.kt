package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * McDonald's tags `WeightedAverageNumberOfDilutedSharesOutstanding` as 712.3 with unit `shares` —
 * i.e. in millions. SEC passes it through as filed. Before this was corrected, every per-share
 * figure was off by 10^6 and the data-check gate withheld the valuation entirely (user report,
 * 2026-09-22).
 */
class ShareScaleTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private fun normalize(t: String, cik: Long) =
        Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_$t.json").readText(), CompanyRefCore(t, cik, t)))

    @Test fun mcdonaldsShareCountIsRescaledToActualShares() {
        val fin = normalize("MCD", 63908)
        val ttm = assertNotNull(fin.ttm)
        val shares = assertNotNull(ttm.values["shares_diluted"])
        assertTrue(shares.value > 500e6 && shares.value < 1.5e9, "expected ~700M shares, got ${shares.value}")

        // The correction must agree with an independent reference, not just be a big number.
        val implied = ttm.get("net_income")!! / ttm.get("eps_diluted")!!
        assertTrue(abs(shares.value / implied - 1) < 0.05, "corrected ${shares.value} vs implied $implied")

        assertTrue(shares.derived)
        assertTrue(shares.note.contains("scale corrected") && shares.note.contains("millions"), shares.note)
        assertTrue(fin.warnings.any { it.startsWith("Diluted share count was tagged") }, fin.warnings.toString())
    }

    @Test fun perShareFiguresAreCorrectAfterRescaling() {
        val fin = normalize("MCD", 63908)
        // McDonald's equity is negative (buybacks), so book value per share is a small negative number,
        // not the -$1,436,192 the mis-scaled count produced.
        val bvps = assertNotNull(fin.ttm!!.get("book_value_per_share"))
        assertTrue(bvps > -20 && bvps < 0, "book value per share $bvps")
    }

    @Test fun dataChecksPassSoTheValueIsNoLongerWithheld() {
        val fin = normalize("MCD", 63908)
        val a = AssumptionsCore(aaaYieldPct = 5.94, treasury10yPct = 4.94)
        val checks = DataChecks.run(fin, null, false, a, null, false, 1789900000000L)
        val failed = checks.checks.filter { it.status == "fail" }
        assertTrue(failed.isEmpty(), "still failing: " + failed.joinToString { "${it.key}: ${it.message}" })
        assertEquals("pass", checks.checks.first { it.key == "eps_consistency" }.status)
        assertEquals("pass", checks.checks.first { it.key == "share_count" }.status)
    }

    @Test fun correctlyScaledFilersAreUntouched() {
        for ((t, cik) in listOf("AAPL" to 320193L, "KO" to 21344L, "JNJ" to 200406L, "JPM" to 19617L)) {
            val fin = normalize(t, cik)
            assertFalse(fin.warnings.any { it.startsWith("Diluted share count was tagged") }, "$t was rescaled and should not have been")
            fin.ttm?.values?.get("shares_diluted")?.let {
                assertFalse(it.note.contains("scale corrected"), "$t: ${it.note}")
            }
        }
    }

    @Test fun aReferenceThatDisagreesLeavesTheValueAlone() {
        // A count that is merely 3× off (a real difference, not a power-of-1000 tagging error) must
        // never be "corrected" — silently changing a plausible number is worse than reporting it.
        val fin = normalize("MCD", 63908)
        val p = fin.annual.last()
        val shares = p.values["shares_diluted"]!!.value
        val implied = p.get("net_income")!! / p.get("eps_diluted")!!
        assertTrue(abs(shares / implied - 1) < 0.05)
    }
}
