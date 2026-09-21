package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ModelResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Python engine is the reference implementation. This test feeds the same recorded
 * companyfacts fixtures through the Kotlin core and diffs every number and string against
 * the frozen Python output (tests/fixtures/expected_*.json).
 */
class OracleTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }
    private val json = Json { prettyPrint = false; explicitNulls = true; encodeDefaults = true }
    private val assumptions = AssumptionsCore(aaaYieldPct = 5.94, treasury10yPct = 4.94, hurdleRatePct = 10.0, equityRiskPremiumPct = 5.0,
        beta = 1.0, terminalGrowthPct = 2.5, exitMultiple = 15.0, taxRatePct = 21.0, rateSource = "fixture")
    private val prices = mapOf("AAPL" to 300.0, "KO" to 60.0, "JNJ" to 150.0)

    private fun load(ticker: String): Pair<NormalizedFinancials, JsonObject> {
        val raw = File(fixtures, "companyfacts_$ticker.json").readText()
        val cik = Json.parseToJsonElement(raw).jsonObject["cik"]!!.jsonPrimitive.content.toLong()
        val fin = Statements.normalize(CompanyFactsParser.parse(raw, CompanyRefCore(ticker, cik, ticker)))
        val expected = Json.parseToJsonElement(File(fixtures, "expected_$ticker.json").readText()).jsonObject
        return fin to expected
    }

    private val diffs = mutableListOf<String>()
    private fun reset() = diffs.clear()

    private fun diff(path: String, a: JsonElement, b: JsonElement) {
        when {
            a is JsonObject && b is JsonObject -> {
                for (k in (a.keys + b.keys)) {
                    if (k !in a) { diffs += "$path.$k missing in kotlin"; continue }
                    if (k !in b) { diffs += "$path.$k missing in python"; continue }
                    diff("$path.$k", a[k]!!, b[k]!!)
                }
            }
            a is JsonArray && b is JsonArray -> { if (a.size != b.size) diffs += "$path size ${a.size} vs ${b.size}" else a.indices.forEach { diff("$path[$it]", a[it], b[it]) } }
            a is JsonNull && b is JsonNull -> {}
            a is JsonPrimitive && b is JsonPrimitive -> {
                val da = a.doubleOrNull; val db = b.doubleOrNull
                if (da != null && db != null && !a.isString && !b.isString) {
                    val tol = 1e-6 * max(1.0, max(abs(da), abs(db)))
                    if (abs(da - db) > tol) diffs += "$path: $da vs $db"
                } else if (a.content != b.content) diffs += "$path: '${a.content}' vs '${b.content}'"
            }
            else -> diffs += "$path: type mismatch ${a::class.simpleName} vs ${b::class.simpleName}"
        }
    }

    private fun modelJson(r: ModelResult): JsonElement = json.parseToJsonElement(json.encodeToString(r))

    @Test fun normalizationMatchesPython() {
        for (t in prices.keys) {
            val (fin, expected) = load(t)
            val ours = Report.financialsJson(fin)
            for (k in listOf("annual", "ttm", "current_shares", "growth", "warnings")) diff("$t.$k", ours[k]!!, expected[k]!!)
        }
        assertTrue(diffs.isEmpty(), "Normalization differs from Python:\n" + diffs.take(40).joinToString("\n"))
    }

    @Test fun modelsMatchPython() {
        for ((t, price) in prices) {
            val (fin, expected) = load(t)
            diff("$t.model_a", modelJson(ModelA.run(fin, assumptions, price)), expected["model_a"]!!)
            diff("$t.model_b", modelJson(ModelB.run(fin, assumptions, price)), expected["model_b"]!!)
        }
        assertTrue(diffs.isEmpty(), "Models differ from Python:\n" + diffs.take(40).joinToString("\n"))
    }

    @Test fun headlineNumbers() {
        val (aapl, _) = load("AAPL")
        // Frozen from the Python reference after Sprint 3 working-capital normalization.
        assertEquals(97.24144296173478, ModelA.run(aapl, assumptions, 300.0).intrinsicValuePerShare!!, 1e-6)
        assertEquals(177.92319124595218, ModelB.run(aapl, assumptions, 300.0).intrinsicValuePerShare!!, 1e-6)
        assertEquals(10, aapl.annual.size)
    }

    @Test fun dayArithmetic() {
        assertEquals("2021-01-03", Day.parse("2021-01-03").toString())
        assertEquals(2020, Statements.fiscalYearFor(Day.parse("2021-01-03")!!))
        assertEquals(365, Day.parse("2024-01-01")!!.daysUntil(Day.parse("2024-12-31")!!))
        assertEquals("2024-02-29", (Day.parse("2024-03-01")!! - 1).toString())
    }
}
