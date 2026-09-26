package com.swcsoftware.valuelens.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fill a companyfacts lag from the filing itself (ISSUES #53) — mirror of services/valuation-engine
 * `edgar/gapfill.py`.
 *
 * SEC's companyfacts feed sometimes goes months without ingesting a filing: Coca-Cola's 10-Q filed
 * 2026-07-29 was still absent in late September, so the trailing twelve months stopped in April (5 of 77
 * universe filers were a quarter behind). The filing's own XBRL instance has the numbers.
 *
 * Entered only when the filer's submissions list a 10-Q/10-K filed after anything in companyfacts; every
 * other filer returns the same `CompanyFacts` instance and nothing is fetched. Reads undimensioned facts
 * for mapped tags only — exactly what companyfacts itself would have carried — and adds them as facts of
 * that filing, so the normalizer treats them as if SEC had caught up. Always says so in a note.
 *
 * A targeted scanner like `ShareClasses`, not an XML parser. No `\b` or lookarounds (Kotlin/Native).
 */
object GapFill {
    class Filing(val accession: String, val primaryDocument: String, val form: String, val filed: Day, val period: String?) {
        fun instanceRef(cik: Long) = InstanceRef(cik, accession, primaryDocument, form, filed)
    }

    private val FILING_FORMS = listOf("10-Q", "10-K")
    private val json = Json { ignoreUnknownKeys = true }
    private val CTX = Regex("""<(?:xbrli:)?context\s+id="([^"]+)"\s*>(.*?)</(?:xbrli:)?context>""", RegexOption.DOT_MATCHES_ALL)
    private val UNIT = Regex("""<(?:xbrli:)?unit\s+id="([^"]+)"\s*>(.*?)</(?:xbrli:)?unit>""", RegexOption.DOT_MATCHES_ALL)
    private val START = Regex("""<(?:xbrli:)?startDate>([^<]+)<""")
    private val END = Regex("""<(?:xbrli:)?endDate>([^<]+)<""")
    private val INSTANT = Regex("""<(?:xbrli:)?instant>([^<]+)<""")
    private val MEASURE = Regex("""<(?:xbrli:)?measure>([^<]+)<""")
    private val FACT = Regex("""<(us-gaap|dei):([A-Za-z0-9_]+)\s([^>]*)>([^<]*)</""")
    private val ATTR = Regex("""(contextRef|unitRef)="([^"]+)"""")

    /** Newest 10-Q/10-K in the submissions JSON (listed newest first). */
    fun latestFiling(submissionsText: String): Filing? = runCatching {
        val r = json.parseToJsonElement(submissionsText).jsonObject["filings"]!!.jsonObject["recent"]!!.jsonObject
        val forms = r["form"]!!.jsonArray.map { it.jsonPrimitive.content }
        val i = forms.indexOfFirst { it in FILING_FORMS }
        if (i < 0) return null
        Filing(r["accessionNumber"]!!.jsonArray[i].jsonPrimitive.content, r["primaryDocument"]!!.jsonArray[i].jsonPrimitive.content, forms[i],
            Day.parse(r["filingDate"]!!.jsonArray[i].jsonPrimitive.content)!!, r["reportDate"]?.jsonArray?.getOrNull(i)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
    }.getOrNull()

    fun latestFiled(cf: CompanyFacts): Day? =
        cf.tagKeys.flatMap { cf.factsFor(it) }.filter { f -> FILING_FORMS.any { f.form.startsWith(it) } }.maxOfOrNull { it.filed }

    fun needsFill(cf: CompanyFacts, filing: Filing?): Boolean {
        if (filing == null) return false
        val newest = latestFiled(cf) ?: return false
        if (filing.filed <= newest) return false
        return cf.tagKeys.none { k -> cf.factsFor(k).any { it.accession == filing.accession } }
    }

    private fun unit(body: String): String? {
        val measures = MEASURE.findAll(body).map { it.groupValues[1].substringAfterLast(":") }.toList()
        if ("divide" in body) return if (measures == listOf("USD", "shares")) "USD/shares" else null
        return if (measures.size == 1) mapOf("USD" to "USD", "shares" to "shares")[measures[0]] else null
    }

    /** Undimensioned facts for tags the concept map reads, as Facts of `filing`. */
    fun parseInstance(xml: String, filing: Filing): List<Fact> {
        val wanted = Concepts.ALL.flatMap { c -> c.tags.map { "${c.taxonomy}:$it" } }.toSet()
        val contexts = HashMap<String, Pair<Day?, Day>>()
        for (m in CTX.findAll(xml)) {
            val body = m.groupValues[2]
            if ("segment" in body || "scenario" in body) continue      // dimensioned: companyfacts drops these too
            val end = (END.find(body) ?: INSTANT.find(body))?.let { Day.parse(it.groupValues[1].trim()) } ?: continue
            contexts[m.groupValues[1]] = START.find(body)?.let { Day.parse(it.groupValues[1].trim()) } to end
        }
        val units = UNIT.findAll(xml).associate { it.groupValues[1] to unit(it.groupValues[2]) }
        val out = LinkedHashMap<String, Fact>()
        for (m in FACT.findAll(xml)) {
            val (taxonomy, tag, attrs, text) = m.destructured
            if ("$taxonomy:$tag" !in wanted) continue
            val a = ATTR.findAll(attrs).associate { it.groupValues[1] to it.groupValues[2] }
            val ctx = contexts[a["contextRef"]] ?: continue
            val u = units[a["unitRef"]] ?: continue
            val value = text.trim().toDoubleOrNull() ?: continue
            val key = "$taxonomy:$tag|$u|${ctx.first}|${ctx.second}"
            if (key !in out) out[key] = Fact(taxonomy, tag, u, value, ctx.second, ctx.first, filing.form, null, null, filing.accession, filing.filed, null)
        }
        return out.values.toList()
    }

    fun merge(cf: CompanyFacts, extra: List<Fact>): CompanyFacts {
        val facts = LinkedHashMap<String, List<Fact>>()
        for (k in cf.tagKeys) facts[k] = cf.factsFor(k)
        for (f in extra) facts["${f.taxonomy}:${f.tag}"] = (facts["${f.taxonomy}:${f.tag}"] ?: emptyList()) + f
        return CompanyFacts(cf.ref, cf.entityName, facts)
    }

    fun noteFor(filing: Filing, n: Int): String =
        "SEC's companyfacts feed has not yet published the ${filing.form} filed ${filing.filed}${filing.period?.let { ", period ending $it" } ?: ""}; " +
            "$n figures were read directly from that filing's XBRL."

    /** Returns `cf` itself (untouched, nothing fetched) unless companyfacts lags the filer's latest filing. */
    fun fill(cf: CompanyFacts, submissionsText: String?, instance: (InstanceRef) -> String?): Pair<CompanyFacts, String?> {
        val filing = submissionsText?.let { latestFiling(it) }
        if (!needsFill(cf, filing)) return cf to null
        val xml = instance(filing!!.instanceRef(cf.ref.cik)) ?: return cf to null
        val extra = parseInstance(xml, filing)
        if (extra.isEmpty()) return cf to null
        return merge(cf, extra) to noteFor(filing, extra.size)
    }
}
