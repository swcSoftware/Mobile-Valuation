package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.FilingDocument
import com.swcsoftware.valuelens.domain.FilingUsed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The SEC filings behind a report's numbers (Sprint 9), so a reader can open the filing a figure came
 * from and check it. Two steps, deliberately separate:
 *  - `used`: computed from the normalized values the valuation already has — no network, part of the report.
 *  - `documents`: where each filing's main document lives, from EDGAR's filing list (the submissions JSON,
 *    already cached for the profile). Only called when the company page opens, so the valuation path
 *    never waits on it.
 */
object Filings {
    private val FORMS = listOf("10-K", "10-Q")
    private val json = Json { ignoreUnknownKeys = true }

    /** Distinct filings a figure was read from, newest first. Computed values (`valuelens:derived`) are skipped. */
    fun used(fin: NormalizedFinancials): List<FilingUsed> {
        // (period label, concept key, value) — the report's periods in order, then TTM, then the cover-page count.
        val values = fin.annual.flatMap { p -> p.values.map { Triple(p.label, it.key, it.value) } } +
            (fin.ttm?.let { t -> t.values.map { Triple("TTM", it.key, it.value) } } ?: emptyList()) +
            listOfNotNull(fin.currentShares?.let { Triple("TTM", "shares_outstanding", it) })
        val byAccession = LinkedHashMap<String, MutableList<Triple<String, String, SourcedValueCore>>>()
        for (v in values) {
            val sv = v.third
            if (sv.taxonomy == "valuelens" || sv.accession.isBlank() || FORMS.none { sv.form.startsWith(it) }) continue
            byAccession.getOrPut(sv.accession) { mutableListOf() } += v
        }
        val order = Concepts.ALL.map { it.key }
        val periodOrder = fin.annual.map { it.label } + "TTM"
        return byAccession.map { (acc, items) ->
            val first = items.first().third
            FilingUsed(acc, first.form, first.filed.toString(),
                items.map { it.first }.distinct().sortedBy { periodOrder.indexOf(it) },
                items.map { it.second }.distinct().sortedBy { k -> order.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } })
        }.sortedByDescending { it.filed }
    }

    /** "FY2022–FY2023", "TTM", "FY2025 and TTM" — the years a filing supplied, as the list shows them. */
    fun supplied(periods: List<String>): String {
        val fy = periods.filter { it != "TTM" }
        val years = when (fy.size) { 0 -> ""; 1 -> fy[0]; else -> "${fy.first()}–${fy.last()}" }
        return listOf(years, if ("TTM" in periods) "TTM" else "").filter { it.isNotEmpty() }.joinToString(" and ")
    }

    fun indexUrl(cik: Long, accession: String) =
        "https://www.sec.gov/Archives/edgar/data/$cik/${accession.replace("-", "")}/$accession-index.htm"

    fun documentUrl(cik: Long, accession: String, primaryDocument: String) =
        "https://www.sec.gov/Archives/edgar/data/$cik/${accession.replace("-", "")}/$primaryDocument"

    /**
     * Main-document URLs for `filings`. `submissionsText` is the filer's EDGAR filing list; `page(name)` fetches one
     * of its older pages. Heavy filers (JPM files ~2,000 prospectuses a month) push older 10-Ks off the "recent"
     * block, so a filing not found there is looked up in the one older page whose date range covers it.
     */
    fun documents(filings: List<FilingUsed>, cik: Long, submissionsText: String?, page: (String) -> String?): List<FilingDocument> {
        val primary = HashMap<String, Pair<String, String>>()   // accession → (primary document, report date)
        val root = submissionsText?.let { runCatching { json.parseToJsonElement(it).jsonObject["filings"]?.jsonObject }.getOrNull() }
        root?.get("recent")?.jsonObject?.let { primary += block(it) }
        val older = root?.get("files")?.jsonArray?.mapNotNull { f ->
            val o = f.jsonObject
            Triple(o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["filingFrom"]?.jsonPrimitive?.content ?: "", o["filingTo"]?.jsonPrimitive?.content ?: "")
        } ?: emptyList()
        val fetched = HashSet<String>()
        for (f in filings) {
            if (f.accession in primary) continue
            val name = older.firstOrNull { (_, from, to) -> f.filed in from..to }?.first ?: continue
            if (!fetched.add(name)) continue
            page(name)?.let { text -> runCatching { primary += block(json.parseToJsonElement(text).jsonObject) } }
        }
        return filings.map { f ->
            val (doc, reportDate) = primary[f.accession] ?: ("" to "")
            val rd = reportDate.takeIf { it.isNotBlank() }
            FilingDocument(f, doc.takeIf { it.isNotBlank() }?.let { documentUrl(cik, f.accession, it) }, indexUrl(cik, f.accession), rd,
                title = rd?.let { "${f.form} for the period ending $it" } ?: "${f.form} filed ${f.filed}", supplied = supplied(f.periods))
        }
    }

    /** accession → (primary document, report date), from one block of the filing list ("recent", or an older page). */
    private fun block(o: JsonObject): Map<String, Pair<String, String>> {
        val acc = o["accessionNumber"]?.jsonArray ?: return emptyMap()
        val doc = o["primaryDocument"]?.jsonArray ?: return emptyMap()
        val date = o["reportDate"]?.jsonArray
        return acc.indices.associate { acc[it].jsonPrimitive.content to ((doc.getOrNull(it)?.jsonPrimitive?.content ?: "") to (date?.getOrNull(it)?.jsonPrimitive?.content ?: "")) }
    }
}
