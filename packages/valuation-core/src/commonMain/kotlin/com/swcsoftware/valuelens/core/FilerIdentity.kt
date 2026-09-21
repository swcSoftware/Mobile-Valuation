package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.CompanyRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Successor-issuer resolution (Sprint 3, Track A).
 *
 * When a company reorganizes into a new holding company (Rule 12g-3, form 8-K12B), SEC's ticker
 * list points at the NEW CIK, which has no 10-K history yet — e.g. XOM → ExxonMobil Holdings Corp
 * (CIK 2115436) while the filings live under Exxon Mobil Corp (CIK 34088).
 *
 * This runs ONLY when the primary path finds no annual data, so ordinary filers are untouched.
 * A predecessor is accepted only on hard evidence: same SIC code, it files 10-Ks, its latest 10-K
 * predates the successor's first filing and is recent. The substitution is always surfaced.
 */
class FilerProfile(val cik: Long, val name: String, val sic: String?, val firstFiling: Day?, val latest10K: Day?, val hasSuccessorNotice: Boolean, val tickers: List<String>,
                   val forms: Set<String> = emptySet()) {
    val isForeignPrivateIssuer get() = forms.any { it.startsWith("20-F") || it.startsWith("40-F") }
    val isNewListing get() = forms.any { it == "S-1" || it == "424B4" || it == "S-11" || it.startsWith("F-1") }
    val isFund get() = forms.any { it.startsWith("N-CSR") || it.startsWith("N-1A") || it == "N-2" }
}

class Predecessor(val ref: CompanyRef, val successorName: String, val successorFirstFiling: Day?, val viaSuccessorNotice: Boolean)

object FilerIdentity {
    private val json = Json { ignoreUnknownKeys = true }
    private val SUFFIXES = setOf("THE", "HOLDINGS", "HOLDING", "CORP", "CORPORATION", "INC", "CO", "COMPANY", "LTD", "PLC", "GROUP", "LLC", "SA", "NV", "AG")
    const val MAX_PREDECESSOR_10K_AGE_DAYS = 550L

    fun submissionsUrl(cik: Long) = "https://data.sec.gov/submissions/CIK${cik.toString().padStart(10, '0')}.json"
    fun entitySearchUrl(prefix: String) = "https://efts.sec.gov/LATEST/search-index?keysTyped=" + prefix.lowercase().replace(" ", "%20")

    fun parseProfile(text: String): FilerProfile? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val recent = o["filings"]?.jsonObject?.get("recent")?.jsonObject
        val forms = recent?.get("form")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val dates = recent?.get("filingDate")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val tenK = forms.indices.filter { forms[it] == "10-K" }.mapNotNull { Day.parse(dates[it]) }.maxOrNull()
        FilerProfile(
            cik = o["cik"]!!.jsonPrimitive.content.toLong(), name = o["name"]?.jsonPrimitive?.content ?: "",
            sic = o["sic"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            firstFiling = dates.mapNotNull { Day.parse(it) }.minOrNull(), latest10K = tenK,
            hasSuccessorNotice = forms.any { it.startsWith("8-K12B") || it.startsWith("8-K12G3") },
            tickers = o["tickers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            forms = forms.toSet(),
        )
    }.getOrNull()

    /** First meaningful word of a company name ("ExxonMobil Holdings Corp" → "exxonmobil"; "The Coca-Cola Co" → "coca-cola"). */
    /** Honest explanation for a filer with no 10-K data, from its filing history. */
    fun noAnnualDataReason(ticker: String, p: FilerProfile?): String = when {
        p == null -> "$ticker has no 10-K income statement data on EDGAR (foreign filer, fund, SPAC or new listing)."
        p.isForeignPrivateIssuer -> "$ticker (${p.name}) is a foreign private issuer that files 20-F/40-F reports, which ValueLens doesn't parse yet."
        p.isFund -> "$ticker (${p.name}) is a fund or trust, not an operating company; it has no 10-K to value."
        p.isNewListing && p.latest10K == null -> "$ticker (${p.name}) listed recently${p.firstFiling?.let { " (first SEC filing $it)" } ?: ""} and hasn't filed its first 10-K yet. Come back after its fiscal year-end report."
        p.latest10K == null -> "$ticker (${p.name}) has no 10-K on file with SEC."
        else -> "$ticker (${p.name}) files 10-Ks but none carry XBRL income-statement facts ValueLens can read."
    }

    fun searchToken(name: String): String? =
        name.uppercase().replace(",", " ").replace(".", " ").split(Regex("\\s+")).map { it.trim() }.firstOrNull { it.isNotEmpty() && it !in SUFFIXES }?.lowercase()

    /** The token, then shorter prefixes (≥ 4 chars) — "exxonmobil" → ["exxonmobil", "exxon", "exxo"]. */
    fun searchPrefixes(token: String): List<String> = listOf(token, token.take(5), token.take(4)).filter { it.length >= 4 }.distinct()

    /** Candidate (cik, name) pairs from the entity autocomplete, excluding the successor. */
    fun parseCandidates(text: String, excludeCik: Long): List<Pair<Long, String>> = runCatching {
        json.parseToJsonElement(text).jsonObject["hits"]!!.jsonObject["hits"]!!.jsonArray.mapNotNull { h ->
            val o = h.jsonObject; val cik = o["_id"]!!.jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
            if (cik == excludeCik) null else cik to (o["_source"]!!.jsonObject["entity"]?.jsonPrimitive?.content ?: "").substringBefore(" (").trim()
        }
    }.getOrDefault(emptyList())

    /**
     * Substitution requires hard evidence of a Rule 12g-3 succession: the successor MUST have filed an
     * 8-K12B/8-K12G3. Without it (an IPO, a spin-off named after its parent) nothing is substituted —
     * showing the wrong company's numbers is worse than showing none.
     */
    fun isPlausiblePredecessor(successor: FilerProfile, candidate: FilerProfile, today: Day): Boolean {
        if (!successor.hasSuccessorNotice) return false
        val tenK = candidate.latest10K ?: return false
        if (successor.sic != null && candidate.sic != null && successor.sic != candidate.sic) return false
        if (tenK.daysUntil(today) > MAX_PREDECESSOR_10K_AGE_DAYS) return false
        successor.firstFiling?.let { if (tenK > it + 30) return false }  // predecessor's last 10-K should precede the successor
        return true
    }
}
