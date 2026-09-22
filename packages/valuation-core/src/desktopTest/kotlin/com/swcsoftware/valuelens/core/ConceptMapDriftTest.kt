package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The tag map exists twice — `Concepts.kt` (ships) and `tags.py` (the oracle). They are mirrored by
 * hand, so drift would make the oracle diff meaningless in exactly the place it matters most.
 * This parses both sources and asserts they define the same concepts with the same tags, in order.
 */
class ConceptMapDriftTest {
    private val repo = generateSequence(File(".").absoluteFile) { it.parentFile }.first { File(it, "services/valuation-engine").isDirectory }
    private val kotlinSrc = File(repo, "packages/valuation-core/src/commonMain/kotlin/com/swcsoftware/valuelens/core/Concepts.kt")
    private val pythonSrc = File(repo, "services/valuation-engine/valuation_engine/normalize/tags.py")

    private data class Entry(val key: String, val kind: String, val unit: String, val taxonomy: String, val requirement: String, val tags: List<String>)

    private fun quoted(s: String) = Regex("\"([^\"]*)\"").findAll(s).map { it.groupValues[1] }.toList()

    /** Split a source file into one chunk per `Concept(` call. */
    private fun chunks(text: String): List<String> =
        text.split("Concept(").drop(1).map { it.substringBefore("\nCONCEPTS_BY_KEY").substringBefore("\n    val BY_KEY") }

    private fun parse(text: String, tagsOpener: String): List<Entry> = chunks(text).mapNotNull { chunk ->
        val key = quoted(chunk).firstOrNull() ?: return@mapNotNull null
        val kind = Regex("Kind\\.(\\w+)").find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
        val open = chunk.indexOf(tagsOpener).takeIf { it >= 0 } ?: return@mapNotNull null
        val body = chunk.substring(open + tagsOpener.length, chunk.indexOf(')', open))
        val rest = chunk.substring(chunk.indexOf(')', open))
        val unit = Regex("""unit\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "USD"
        val taxonomy = Regex("""taxonomy\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "us-gaap"
        val requirement = Regex("""requirement\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "optional"
        Entry(key, kind, unit, taxonomy, requirement, quoted(body))
    }

    @Test fun kotlinAndPythonTagMapsAreIdentical() {
        val kt = parse(kotlinSrc.readText(), "listOf(")
        val py = parse(pythonSrc.readText(), "Kind.")
            .let { parsePythonTuples() }   // python tags live in a tuple, parsed separately
        assertTrue(kt.isNotEmpty(), "parsed no concepts from Concepts.kt")
        assertEquals(kt.size, py.size, "concept count differs: kotlin ${kt.map { it.key }} vs python ${py.map { it.key }}")

        val problems = mutableListOf<String>()
        for ((k, p) in kt.zip(py)) {
            if (k.key != p.key) { problems += "order differs: '${k.key}' vs '${p.key}'"; continue }
            if (k.kind != p.kind) problems += "${k.key}: kind ${k.kind} vs ${p.kind}"
            if (k.unit != p.unit) problems += "${k.key}: unit ${k.unit} vs ${p.unit}"
            if (k.taxonomy != p.taxonomy) problems += "${k.key}: taxonomy ${k.taxonomy} vs ${p.taxonomy}"
            if (k.requirement != p.requirement) problems += "${k.key}: requirement ${k.requirement} vs ${p.requirement}"
            if (k.tags != p.tags) problems += "${k.key}: tags differ\n    kotlin: ${k.tags}\n    python: ${p.tags}"
        }
        if (problems.isNotEmpty()) fail("Concept map drift between Concepts.kt and tags.py:\n" + problems.joinToString("\n"))
    }

    /** Python tags sit in a `(...)` tuple after the Kind, which may span lines. */
    private fun parsePythonTuples(): List<Entry> = chunks(pythonSrc.readText()).mapNotNull { chunk ->
        val key = quoted(chunk).firstOrNull() ?: return@mapNotNull null
        val kindMatch = Regex("Kind\\.(\\w+),\\s*\\(").find(chunk) ?: return@mapNotNull null
        val open = kindMatch.range.last
        val close = chunk.indexOf(')', open)
        val body = chunk.substring(open + 1, close)
        val rest = chunk.substring(close)
        Entry(key, kindMatch.groupValues[1],
            Regex("""unit\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "USD",
            Regex("""taxonomy\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "us-gaap",
            Regex("""requirement\s*=\s*"([^"]+)"""").find(rest)?.groupValues?.get(1) ?: "optional",
            quoted(body))
    }

    @Test fun parserSeesTheConceptsWeExpect() {
        val kt = parse(kotlinSrc.readText(), "listOf(")
        assertEquals(Concepts.ALL.size, kt.size, "parser missed concepts defined in Concepts.kt")
        assertEquals(Concepts.ALL.map { it.key }, kt.map { it.key })
        val revenue = kt.first { it.key == "revenue" }
        assertTrue(revenue.tags.contains("Revenues") && revenue.tags.size >= 5, revenue.tags.toString())
        assertEquals("USD/shares", kt.first { it.key == "eps_diluted" }.unit)
        assertEquals("dei", kt.first { it.key == "shares_outstanding" }.taxonomy)
        assertEquals("required", kt.first { it.key == "revenue" }.requirement)
        assertEquals("expected", kt.first { it.key == "capex" }.requirement)
        assertEquals("optional", kt.first { it.key == "gain_on_sale" }.requirement)
        assertEquals(Concepts.ALL.map { it.requirement }, kt.map { it.requirement })
    }
}
