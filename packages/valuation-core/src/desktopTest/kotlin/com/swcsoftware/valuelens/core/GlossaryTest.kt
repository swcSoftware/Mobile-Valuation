package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards `labels/glossary.json` and the tap-to-define linker (Sprint 8). The owner found Expert Mode's
 * abbreviations hard to remember and the trip to the Glossary and back too slow; every abbreviation on
 * screen now links to a definition, and this test fails the build if a new one appears without one.
 */
class GlossaryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val tickers = listOf("AAPL", "MSFT", "KO", "MCD", "BRK-B", "JPM", "PGR", "O", "AGNC", "CRWV", "PLTR")
    private fun report(t: String): ValuationReport = json.decodeFromString(javaClass.getResource("/fixtures/$t.json")!!.readText())
    private fun keys(text: String) = Glossary.link(text).mapNotNull { it.key }

    @Test fun fileParsesAndEveryEntryIsComplete() {
        assertTrue(Glossary.version.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(GLOSSARY_JSON.length < 60_000, "the JVM caps a string constant at 64 KB")
        val keys = Glossary.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "a key is listed twice")
        Glossary.entries.forEach { e ->
            assertTrue(e.term.isNotBlank() && e.plain.isNotBlank() && e.expert.isNotBlank() && e.aliases.isNotEmpty(), "${e.key} is incomplete")
        }
        val aliases = Glossary.entries.flatMap { e -> e.aliases.map { it to e.key } }
        val clash = aliases.groupBy { it.first.lowercase() }.filterValues { v -> v.map { it.second }.toSet().size > 1 }.keys
        assertTrue(clash.isEmpty(), "these aliases point at two entries: $clash")
    }

    @Test fun theOriginalTenEntriesAreStillThere() {
        for (k in listOf("intrinsic_value", "margin_of_safety", "owner_earnings", "free_cash_flow", "roic", "wacc", "beta", "book_value", "graham_number", "data_checks"))
            assertTrue(Glossary.entry(k) != null, k)
    }

    @Test fun linkingNeverChangesTheText() {
        for (t in tickers) for (m in report(t).modelA.metrics + report(t).modelB.metrics) {
            val f = DisplayLabels.formula(m.formula)
            assertEquals(f, Glossary.link(f).joinToString("") { it.text })
        }
    }

    @Test fun linkingRules() {
        assertEquals(listOf("roic", "nopat"), keys("ROIC = NOPAT / invested capital"))
        assertEquals(listOf("nnwc"), keys("NNWC per share"), "longest alias wins: NNWC, not NWC")
        assertEquals(listOf("fcff"), keys("free cash flow to the firm"))
        assertEquals(emptyList(), keys("Kept the brake on the market"), "Ke and TV never match inside a word")
        assertEquals(listOf("cost_of_equity"), keys("Ke = rf + β × ERP").take(1))
        assertEquals(listOf("cost_of_equity", "risk_free", "beta", "erp"), keys("Ke = max(rf + β × ERP, rf + 4%)"))
        assertEquals(listOf("owner_earnings"), keys("Owner earnings, then owner earnings again"), "first mention only, any case")
        assertEquals(emptyList(), keys("roic"), "capitalised aliases are case-sensitive")
        assertEquals(listOf("sec"), keys("the SEC's filing"))
        assertEquals(listOf("reit"), keys("mortgage REITs"))
        assertEquals(listOf("fcff", "wacc"), keys("Σ FCFF_t/(1+WACC)^t"), "a subscript doesn't hide the term")
    }

    /**
     * Every abbreviation Expert Mode shows — metric labels, formulas, input names, notes, source notes,
     * data-check messages and the report card's verdicts and graded facts, across the eleven sample
     * reports as the labels layer renders them — must link.
     */
    @Test fun everyAbbreviationOnScreenHasADefinition() {
        val abbrev = Regex("(?<![A-Za-z])([A-Z][a-z]?[A-Z][A-Za-z]*|[A-Z]{2,}s?|P/B|P/E|V\\*|Ke|Kd|D&A)(?![A-Za-z])")
        val notTerms = tickers.flatMap { it.split("-") }.toSet() + setOf("USD", "II")
        val missing = sortedMapOf<String, String>()
        for (t in tickers) {
            val r = report(t)
            val texts = mutableListOf<String>()
            for (m in r.modelA.metrics + r.modelB.metrics) {
                texts += m.label; texts += DisplayLabels.formula(m.formula)
                texts += m.inputs.keys.map { DisplayLabels.input(it, m.key) }
                texts += m.notes.map { DisplayLabels.sentence(it) }
                texts += m.sources.map { DisplayLabels.sentence(it.note) }
            }
            // The report card's own prose (Sprint 8 follow-up: the owner found it unlinked).
            texts += Explain.verdictSentence(r, r.modelA); texts += Explain.verdictSentence(r, r.modelB)
            for (lens in Lens.entries) {
                val card = Grading.reportCard(r, lens)
                texts += card.lensBlurb; card.blankNote?.let { texts += it }
                card.facts.forEach { f -> texts += f.label; texts += f.rule; texts += f.why }
            }
            texts += r.dataChecks.map { DisplayLabels.sentence(it.message) }
            texts += r.warnings.map { DisplayLabels.sentence(it) }
            for (text in texts) for (hit in abbrev.findAll(text)) {
                val word = hit.groupValues[1]
                if (word in notTerms) continue
                if (word in CompanyNames.display(r.company.name, r.company.ticker).split(" ", ",", ".")) continue   // "JPMorgan", "McDonalds"
                if (Glossary.link(word).none { it.key != null }) missing.putIfAbsent(word, "$t: ${text.take(90)}")
            }
        }
        assertTrue(missing.isEmpty(), "add these to labels/glossary.json (aliases): " + missing.entries.joinToString("\n  ", "\n  ") { "${it.key}  — ${it.value}" })
    }
}
