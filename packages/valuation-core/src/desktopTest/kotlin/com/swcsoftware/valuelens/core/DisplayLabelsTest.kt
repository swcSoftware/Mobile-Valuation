package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards `labels/display-labels.json`, the file the owner edits to change what the app calls things
 * (Sprint 6). Each test is a mistake someone could plausibly make in that file, caught at build time
 * instead of on a user's screen.
 */
class DisplayLabelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rawFile = File("labels/display-labels.json")
    private val tickers = listOf("AAPL", "MSFT", "KO", "MCD", "BRK-B", "JPM", "PGR", "O", "AGNC", "CRWV", "PLTR")
    private fun report(t: String): ValuationReport =
        json.decodeFromString(javaClass.getResource("/fixtures/$t.json")!!.readText())

    /** snake_case — how a machine key looks once it leaks into text. */
    private val snake = Regex("\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b")

    @Test fun theFileParsesAndIsVersioned() {
        assertTrue(DisplayLabels.version.matches(Regex("\\d{4}-\\d{2}-\\d{2}")), "version should be a date")
        assertTrue(DISPLAY_LABELS_JSON.length < 60_000, "the JVM caps a string constant at 64 KB; split the file before it gets there")
    }

    @Test fun noKeyIsListedTwice() {
        // JSON parsers keep the last duplicate silently, so a second entry would quietly win.
        val text = rawFile.readText()
        for (section in listOf("tags", "concepts", "inputs", "formulaTerms")) {
            val body = Regex("\"$section\"\\s*:\\s*\\{([^}]*)}").find(text)?.groupValues?.get(1)
                ?: error("section $section missing")
            val keys = Regex("\"([^\"]+)\"\\s*:").findAll(body).map { it.groupValues[1] }.toList()
            val dupes = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue(dupes.isEmpty(), "$section lists these twice: $dupes")
        }
    }

    @Test fun everyTagTheConceptMapCanReadHasALabel() {
        val missing = Concepts.ALL.flatMap { c -> c.tags.map { "${c.taxonomy}:$it" } }.filterNot(DisplayLabels::hasTag)
        assertTrue(missing.isEmpty(), "add these to \"tags\" in display-labels.json: $missing")
        assertTrue(DisplayLabels.hasTag("valuelens:derived"))
    }

    @Test fun everyConceptHasALabel() {
        val missing = Concepts.ALL.map { it.key }.filterNot { it in DisplayLabels.file.concepts }
        assertTrue(missing.isEmpty(), "add these to \"concepts\": $missing")
    }

    @Test fun everyInputAModelEmitsHasALabel() {
        val missing = tickers.flatMap { t ->
            val r = report(t)
            (r.modelA.metrics + r.modelA.composite + r.modelB.metrics + r.modelB.composite).flatMap { it.inputs.keys }
        }.toSet().filterNot(DisplayLabels::hasInput)
        assertTrue(missing.isEmpty(), "add these to \"inputs\": $missing")
    }

    @Test fun noMachineKeySurvivesInWarningsChecksOrSourceNotes() {
        val leaks = tickers.flatMap { t ->
            val r = report(t)
            val metrics = r.modelA.metrics + r.modelA.composite + r.modelB.metrics + r.modelB.composite
            // Metric notes were missed the first time and caught on the simulator ("min(capex, d_and_a)").
            val texts = r.warnings + r.dataChecks.map { it.message } +
                metrics.flatMap { m -> m.sources.map { it.note } } + metrics.flatMap { it.notes }
            texts.map(DisplayLabels::sentence).flatMap { s ->
                snake.findAll(s).map { "$t: ${it.value} in \"$s\"" } + if ("us-gaap:" in s) listOf("$t: raw tag in \"$s\"") else emptyList()
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
    }

    @Test fun noMachineKeySurvivesInFormulas() {
        val leaks = tickers.flatMap { t ->
            val r = report(t)
            ((r.modelA.metrics + r.modelA.composite + r.modelB.metrics + r.modelB.composite).map { it.formula } +
                listOf(r.modelA.marginOfSafety.formula, r.modelB.marginOfSafety.formula)).map(DisplayLabels::formula)
                .flatMap { f -> snake.findAll(f).map { "$t: ${it.value} in \"$f\"" } + Regex("\\b(fcf|ffo)\\b").findAll(f).map { "$t: ${it.value} in \"$f\"" } }
        }.distinct()
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
    }

    @Test fun thePlainWordsInAWarningAreNeverTouched() {
        // "shares" and "terminal" are keys *and* English. Only snake_case is rewritten.
        val w = "Share-count discontinuity (2×); earlier per-share data adjusted for 3 shares and a terminal note."
        assertEquals(w, DisplayLabels.sentence(w))
        assertEquals("Dropped stale TTM values (tag no longer reported): short-term investments, long-term debt",
            DisplayLabels.sentence("Dropped stale TTM values (tag no longer reported): short_term_investments, long_term_debt"))
    }

    @Test fun aKeyCanMeanSomethingDifferentInsideOneMetric() {
        assertEquals("Revenue", DisplayLabels.input("revenue"))
        assertEquals("Revenue growth, 5-yr", DisplayLabels.input("revenue", metric = "fcff_growth"),
            "inside Stage-1 growth, revenue is a five-year growth rate, not dollars")
        assertEquals("Fiscal 2024", DisplayLabels.input("FY2024"))
    }

    @Test fun properNounsKeepTheirCapitalMidSentence() {
        val f = DisplayLabels.formula("min(graham_revised, oe_value_hurdle)")
        assertEquals("min(Graham value (revised 1974), owner-earnings value at the hurdle rate)", f)
        assertEquals("diluted earnings per share", DisplayLabels.inSentence("Diluted earnings per share"))
        assertEquals("EPS growth", DisplayLabels.inSentence("EPS growth"), "an acronym is not lower-cased")
    }

    @Test fun anUnlistedTagStillReadsAsWords() {
        // Tags a company files that the concept map does not read — the only place the fallback runs.
        assertEquals("Earnings per share diluted", DisplayLabels.humanizeTag("us-gaap:EarningsPerShareDiluted"))
        assertEquals("Finance lease ROU asset", DisplayLabels.humanizeTag("us-gaap:FinanceLeaseROUAsset"))
        // A listed tag inside a sentence gets its curated label, and the rest of the sentence is kept.
        assertEquals("Income tax expense (reported, not in an annual/TTM context)",
            DisplayLabels.sentence("us-gaap:IncomeTaxExpenseBenefit (reported, not in an annual/TTM context)"))
        // An unlisted one inside a sentence falls back to words rather than leaking the raw tag.
        assertEquals("See Finance lease ROU asset for details.", DisplayLabels.sentence("See us-gaap:FinanceLeaseROUAsset for details."))
    }

    @Test fun theIndexCoversEveryTagInTryOrder() {
        val index = DisplayLabels.index()
        assertEquals(Concepts.ALL.size + 1, index.size, "one entry per concept, plus the derived-figures note")
        val revenue = index.first { it.key == "revenue" }
        assertEquals("Revenue", revenue.term)
        assertEquals(Concepts.ALL.first { it.key == "revenue" }.tags.map { "us-gaap:$it" }, revenue.tags.map { it.tag },
            "tags are listed in the order the concept map tries them")
        assertFalse(index.flatMap { it.tags }.any { it.label.startsWith("us-gaap") }, "every tag in the index has a real label")
    }
}
