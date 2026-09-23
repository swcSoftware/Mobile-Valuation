package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the grades the owner reviewed on a phone (Sprint 5, share-site r4) to the core that now
 * produces them. The fixtures are the same eleven reports the prototype carried.
 *
 * If a threshold in `GradingRules` changes, this fails — on purpose. A grade is a judgement, and a
 * judgement changes only by review: update the rule, bump `GradingRules.VERSION`, and update the
 * expectation here in the same commit, saying why.
 */
class GradingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun report(t: String): ValuationReport {
        val text = javaClass.getResource("/fixtures/$t.json")?.readText()
            ?: error("fixture $t.json missing from src/desktopTest/resources/fixtures")
        return json.decodeFromString(text)
    }

    /** Grades by slot, `-` for anything not graded: profit / debt / conversion / growth. */
    private fun grades(t: String, lens: Lens = Lens.VALUE): String {
        val bySlot = Grading.reportCard(report(t), lens).facts.associateBy { it.slot }
        return listOf("profit", "debt", "conversion", "growth").joinToString(" ") { bySlot.getValue(it).grade ?: "-" }
    }

    @Test fun valueLensReproducesTheReviewedGrades() {
        //                       profit debt conv growth
        assertEquals("A C A B", grades("AAPL"))
        assertEquals("A A D A", grades("MSFT"))
        assertEquals("B D B B", grades("KO"))
        assertEquals("B - B B", grades("MCD"), "negative equity: debt is ungradable, not graded")
        assertEquals("C - - B", grades("BRK-B"), "financial: ROE graded, debt and conversion refused")
        assertEquals("A - - B", grades("JPM"))
        assertEquals("A - - A", grades("PGR"))
        assertEquals("A - - -", grades("AGNC"), "mREIT: ROE graded, no revenue line to grade")
        assertEquals("C A A A", grades("O"), "REIT scale: 5.1% return on capital is a C, not the D a flat rule gave")
        assertEquals("F F A A", grades("CRWV"))
        assertEquals("A - A A", grades("PLTR"))
    }

    @Test fun growthLensGradesDifferentlyAndReadsGrowthFirst() {
        val ko = Grading.reportCard(report("KO"), Lens.GROWTH)
        assertEquals(listOf("growth", "profit", "conversion", "debt"), ko.facts.map { it.slot },
            "the lens sets reading order, so Growth reads as caring about different things")
        assertEquals("D", ko.facts.first().grade, "Coca-Cola's 7.7% a year is a D to a growth investor")
        assertEquals("A C A D", grades("KO", Lens.GROWTH))
    }

    @Test fun theLensNeverTouchesAValuation() {
        // Two readers of the same company see one fair value. Only the judgement of the business moves.
        for (t in listOf("AAPL", "KO", "JPM", "CRWV", "O")) {
            val v = Grading.reportCard(report(t), Lens.VALUE)
            val g = Grading.reportCard(report(t), Lens.GROWTH)
            assertEquals(v.chipA, g.chipA, "$t: the verdict chip moved with the lens")
            assertEquals(v.chipB, g.chipB, "$t: the verdict chip moved with the lens")
            assertEquals(v.facts.associate { it.slot to it.value }, g.facts.associate { it.slot to it.value },
                "$t: a reported figure moved with the lens")
        }
    }

    @Test fun financialsAreJudgedOnReturnOnEquity() {
        val jpm = Grading.reportCard(report("JPM"), Lens.VALUE).facts.first { it.slot == "profit" }
        assertEquals("Return on equity", jpm.label)
        assertEquals("17.4%", jpm.value, "the old flat rule graded JPM's 95.5% return on capital an A")
        assertTrue(jpm.rule.contains("return on equity"), "the substituted measure is named in the rule")
    }

    @Test fun refusalsSayWhyAndCarryNoUnit() {
        val jpm = Grading.reportCard(report("JPM"), Lens.VALUE).facts.associateBy { it.slot }
        for (slot in listOf("debt", "conversion")) {
            val f = jpm.getValue(slot)
            assertEquals("refused", f.state)
            assertNull(f.grade); assertNull(f.value, "a refused measure must not show a number")
            assertTrue(f.why.length > 40, "a refusal must explain itself, not just go blank")
        }
    }

    @Test fun missingNumbersNeverPrintAUnit() {
        val growth = Grading.reportCard(report("AGNC"), Lens.VALUE).facts.first { it.slot == "growth" }
        assertEquals("missing", growth.state)
        assertNull(growth.value, "\"— / yr\" shipped in the prototype once; it must not ship again")
        assertEquals("Not reported", growth.rule)
    }

    @Test fun negativeEquityIsUngradableForThisFilerOnly() {
        val debt = Grading.reportCard(report("MCD"), Lens.VALUE).facts.first { it.slot == "debt" }
        assertEquals("ungradable", debt.state)
        assertTrue(debt.why.contains("negative"), debt.why)
        assertEquals("A", Grading.reportCard(report("MSFT"), Lens.VALUE).facts.first { it.slot == "debt" }.grade,
            "a filer with positive equity is still graded")
    }

    @Test fun fewerThanFiveFiledYearsIsNotATrend() {
        val crwv = Grading.reportCard(report("CRWV"), Lens.VALUE)
        crwv.facts.forEach { assertNull(it.history, "CRWV has three filed years; ${it.slot} drew a trend anyway") }
    }

    @Test fun historicalReadsMatchTheReviewedPrototype() {
        fun phrase(t: String, slot: String) =
            Grading.reportCard(report(t), Lens.VALUE).facts.first { it.slot == slot }.history?.phrase
        assertEquals("Best in 10 years", phrase("JPM", "profit"))
        assertEquals("Above its 10-yr average", phrase("BRK-B", "profit"))
        assertEquals("Above its 10-yr average", phrase("AAPL", "profit"))
        assertEquals("Below its 10-yr average", phrase("AAPL", "conversion"),
            "an A on the fixed rule can still be below the company's own norm — that is the point of two judgements")
        assertEquals("Faster than its 9-yr rate", phrase("AAPL", "growth"))
        assertEquals("Slower than its 9-yr rate", phrase("PGR", "growth"))
        assertNull(phrase("AAPL", "debt"), "no total_debt in the annual series yet (ISSUES #81)")
    }

    @Test fun chipsComeFromTheCore() {
        assertEquals(VerdictChip("Above fair value", "bad"), Grading.reportCard(report("AAPL"), Lens.VALUE).chipA)
        assertEquals(VerdictChip("Thin margin", "mid"), Grading.reportCard(report("AGNC"), Lens.VALUE).chipA)
        assertEquals(VerdictChip("Not valued", "none"), Grading.reportCard(report("CRWV"), Lens.VALUE).chipB)
    }

    @Test fun everyGradedFactPrintsTheRuleThatProducedIt() {
        for (t in listOf("AAPL", "MSFT", "KO", "MCD", "BRK-B", "JPM", "PGR", "AGNC", "O", "CRWV", "PLTR")) {
            for (lens in Lens.entries) {
                Grading.reportCard(report(t), lens).facts.filter { it.grade != null }.forEach {
                    assertTrue(it.rule.startsWith("A at "), "$t/${it.slot}: a grade without its threshold is an opinion")
                    assertNotNull(it.value)
                }
            }
        }
    }

    @Test fun theFacadeRoundTrips() {
        val raw = javaClass.getResource("/fixtures/O.json")!!.readText()
        // Grading reads the report it is handed and nothing else; a fetcher that refuses every
        // request proves no network is involved.
        val offline = object : Fetcher { override fun get(url: String, headers: Map<String, String>) = FetchResult(599, null) }
        val core = ValuationCore(offline, MemCache(), Clock { 0L })
        val out = json.decodeFromString<ReportCardSummary>(core.reportCardJson(raw, "GROWTH"))
        assertEquals("GROWTH", out.lens)
        assertEquals("REIT", out.modeLabel)
        assertEquals(GradingRules.VERSION, out.rulesVersion)
        assertEquals("VALUE", json.decodeFromString<ReportCardSummary>(core.reportCardJson(raw, "nonsense")).lens,
            "an unknown lens falls back to Value rather than failing")
    }
}
