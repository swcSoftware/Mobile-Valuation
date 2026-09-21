package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.ShareClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Multi-class share resolution (Sprint 3, Track C).
 *
 * SEC's companyfacts feed drops facts that carry a dimension, so companies that report per share
 * class (BRK, V, GOOG) show stale or partial share counts. The filing's XBRL instance has them:
 *   dei:EntityCommonStockSharesOutstanding  [StatementClassOfStockAxis = CommonClassXMember]
 *   dei:TradingSymbol                        [same axis]  → which ticker is which class
 *   us-gaap:EarningsPerShareDiluted/Basic    [same axis]  → EPS ratio = economic conversion ratio
 *
 * One valuation for the company, expressed in shares of the class the user searched:
 *   total shares (searched-class equivalents) = Σ shares_c × EPS_c / EPS_searched
 *
 * This is a targeted scanner, not an XML parser: it reads only the contexts and elements above.
 * It runs only when the ordinary share count is missing or inconsistent.
 */
class InstanceRef(val cik: Long, val accession: String, val primaryDocument: String, val form: String, val filed: Day?) {
    val instanceUrl: String get() = "https://www.sec.gov/Archives/edgar/data/$cik/${accession.replace("-", "")}/${primaryDocument.substringBeforeLast(".")}_htm.xml"
}

class ClassResolution(
    val classes: List<ShareClass>, val searchedClass: String?, val totalInSearchedClass: Double,
    val dilutedInSearchedClass: Double?, val source: InstanceRef, val notes: List<String>,
)

object ShareClasses {
    private val json = Json { ignoreUnknownKeys = true }

    /** Latest 10-Q/10-K from the submissions JSON (already fetched for the profile). */
    fun latestFilingRef(submissionsText: String, cik: Long): InstanceRef? = runCatching {
        val r = json.parseToJsonElement(submissionsText).jsonObject["filings"]!!.jsonObject["recent"]!!.jsonObject
        val forms = r["form"]!!.jsonArray.map { it.jsonPrimitive.content }
        val i = forms.indexOfFirst { it == "10-Q" || it == "10-K" }
        if (i < 0) return null
        InstanceRef(cik, r["accessionNumber"]!!.jsonArray[i].jsonPrimitive.content, r["primaryDocument"]!!.jsonArray[i].jsonPrimitive.content, forms[i], Day.parse(r["filingDate"]!!.jsonArray[i].jsonPrimitive.content))
    }.getOrNull()

    // ---- scanner ----
    private class Ctx(val id: String, val classMember: String?, val start: Day?, val end: Day?)
    private class FactRow(val tag: String, val ctx: String, val value: String)

    private val CTX_RE = Regex("""<(?:xbrli:)?context\s+id="([^"]+)"\s*>(.*?)</(?:xbrli:)?context>""", RegexOption.DOT_MATCHES_ALL)
    private val DIM_RE = Regex("""dimension="us-gaap:StatementClassOfStockAxis"\s*>\s*([^<\s]+)\s*<""")
    private val START_RE = Regex("""<(?:xbrli:)?startDate>([^<]+)<"""); private val END_RE = Regex("""<(?:xbrli:)?endDate>([^<]+)<"""); private val INSTANT_RE = Regex("""<(?:xbrli:)?instant>([^<]+)<""")
    private val TAGS = listOf("dei:EntityCommonStockSharesOutstanding", "dei:TradingSymbol", "us-gaap:EarningsPerShareDiluted", "us-gaap:EarningsPerShareBasic", "us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding", "us-gaap:WeightedAverageNumberOfSharesOutstandingBasic")

    /** "us-gaap:CommonClassAMember" / "brka:EquivalentClassBMember" / "v:CommonClassB1Member" → "A" / "B" / "B1". */
    fun classToken(member: String): String? = Regex("""Class([A-Z][0-9]?)(?:Common)?(?:Stock)?Member$""").find(member.substringAfter(":"))?.groupValues?.get(1)

    private fun facts(xml: String): Pair<Map<String, Ctx>, List<FactRow>> {
        val ctxs = CTX_RE.findAll(xml).associate { m ->
            val body = m.groupValues[2]
            m.groupValues[1] to Ctx(m.groupValues[1], DIM_RE.find(body)?.groupValues?.get(1), START_RE.find(body)?.let { Day.parse(it.groupValues[1]) },
                (END_RE.find(body) ?: INSTANT_RE.find(body))?.let { Day.parse(it.groupValues[1]) })
        }
        val rows = TAGS.flatMap { tag -> Regex("""<$tag\b[^>]*contextRef="([^"]+)"[^>]*>([^<]*)</$tag>""").findAll(xml).map { FactRow(tag, it.groupValues[1], it.groupValues[2].trim()) } }
        return ctxs to rows
    }

    /**
     * Resolve classes from an instance document. `searchedTicker` picks the reference class; when the
     * ticker isn't on the cover page (non-traded class) class A / the largest class is used.
     */
    fun resolve(xml: String, searchedTicker: String, source: InstanceRef): ClassResolution? {
        val (ctxs, rows) = facts(xml)
        val notes = mutableListOf<String>()
        fun token(ctx: String) = ctxs[ctx]?.classMember?.let { classToken(it) }

        // 1. Common classes = those with a cover-page share count (excludes notes/preferred on the same axis)
        val counts = rows.filter { it.tag == "dei:EntityCommonStockSharesOutstanding" }.mapNotNull { r -> token(r.ctx)?.let { t -> Triple(t, r.value.toDoubleOrNull() ?: return@mapNotNull null, ctxs[r.ctx]?.end) } }
        val byClass = counts.groupBy { it.first }.mapValues { (_, v) -> v.maxByOrNull { it.third ?: Day(0) }!! }
        if (byClass.isEmpty()) return null

        // 2. Ticker per class
        val symbols = rows.filter { it.tag == "dei:TradingSymbol" }.mapNotNull { r -> token(r.ctx)?.let { it to r.value.uppercase().replace(".", "-") } }.toMap()
        val wanted = Edgar.normalizeTicker(searchedTicker)
        val searched = symbols.entries.firstOrNull { it.value == wanted }?.key

        // 3. EPS per class for the conversion ratio: latest period end, longest duration, diluted preferred
        fun epsFor(cls: String): Double? {
            val cands = rows.filter { (it.tag == "us-gaap:EarningsPerShareDiluted" || it.tag == "us-gaap:EarningsPerShareBasic") && token(it.ctx) == cls && it.value.toDoubleOrNull() != null }
            val best = cands.maxWithOrNull(compareBy<FactRow> { ctxs[it.ctx]?.end ?: Day(0) }.thenBy { ctxs[it.ctx]?.let { c -> (c.start?.let { s -> s.daysUntil(c.end ?: s) } ?: 0L) } ?: 0L }.thenBy { it.tag == "us-gaap:EarningsPerShareDiluted" })
            return best?.value?.toDoubleOrNull()?.takeIf { it != 0.0 }
        }
        val refClass = searched ?: byClass.keys.firstOrNull { it == "A" } ?: byClass.maxByOrNull { it.value.second }!!.key
        if (searched == null) notes += "Ticker $wanted is not a listed class on the cover page; values are expressed per class $refClass share."
        val epsRef = epsFor(refClass)

        // 4. Build classes with ratios
        val classes = byClass.entries.sortedBy { it.key }.map { (cls, v) ->
            val eps = epsFor(cls)
            val ratio = when {
                cls == refClass -> 1.0
                eps != null && epsRef != null -> eps / epsRef
                else -> { notes += "No per-class EPS for class $cls; assumed economically equal to class $refClass."; 1.0 }
            }
            ShareClass(cls, symbols[cls], v.second, ratio, eps, v.third?.toString())
        }
        val total = classes.sumOf { it.shares * it.ratioToSearched }
        // 5. Diluted weighted average in searched-class terms (for the consistency check).
        //    Two presentations exist: per-class counts (Visa: sum them, converted) and "equivalent" counts where each
        //    class's figure is the whole company in that class's units (Berkshire: take the searched class alone).
        fun wavg(cls: String): Double? = rows.filter { (it.tag == "us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding" || it.tag == "us-gaap:WeightedAverageNumberOfSharesOutstandingBasic") && token(it.ctx) == cls }
            .maxWithOrNull(compareBy<FactRow> { ctxs[it.ctx]?.end ?: Day(0) }.thenBy { it.tag.contains("Diluted") })?.value?.toDoubleOrNull()
        val own = wavg(searched ?: refClass)
        val diluted = when {
            own != null && total > 0 && kotlin.math.abs(own / total - 1) < 0.3 -> own       // equivalent presentation (or single dominant class)
            else -> classes.mapNotNull { c -> wavg(c.cls)?.let { it * c.ratioToSearched } }.takeIf { it.size == classes.size }?.sum()
        }
        return ClassResolution(classes, searched ?: refClass, total, diluted, source, notes)
    }
}
