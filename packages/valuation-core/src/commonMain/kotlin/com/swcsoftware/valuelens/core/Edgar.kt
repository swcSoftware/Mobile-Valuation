package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.EngineException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** TTL cache on top of the host's KeyValueCache. Entries are `{"t":millis,"v":text}`. */
internal class TtlCache(private val cache: KeyValueCache, private val clock: Clock) {
    @Serializable private data class Entry(val t: Long, val v: String)
    private val json = Json

    fun get(key: String, ttlMillis: Long): String? {
        val raw = cache.get(key) ?: return null
        val e = runCatching { json.decodeFromString<Entry>(raw) }.getOrNull() ?: return null
        return if (clock.nowMillis() - e.t <= ttlMillis) e.v else null
    }
    fun put(key: String, value: String) = cache.put(key, json.encodeToString(Entry.serializer(), Entry(clock.nowMillis(), value)))
    fun getStale(key: String): String? = cache.get(key)?.let { runCatching { json.decodeFromString<Entry>(it).v }.getOrNull() }
}

/**
 * SEC EDGAR access from the device. The user's own identity goes on every request (fair-access
 * policy) and a per-device courtesy limiter keeps well under SEC's 10 req/s.
 */
class Edgar(private val fetcher: Fetcher, cache: KeyValueCache, private val userAgent: String, private val clock: Clock,
            /** Optional keyless mirror of company_tickers.json (GitHub Pages); SEC is the fallback. */
            private val tickersMirrorUrl: String? = null) {
    private val ttl = TtlCache(cache, clock)
    private var lastCall = 0L

    companion object {
        const val TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"
        const val DAY = 24L * 3600 * 1000
        fun normalizeTicker(raw: String) = raw.trim().uppercase().replace(".", "-").replace(" ", "-")
    }

    private fun headers() = mapOf("User-Agent" to userAgent, "Accept" to "application/json")  // hosts negotiate gzip themselves

    private fun getText(url: String, ttlMillis: Long, headers: Map<String, String> = headers()): String {
        ttl.get(url, ttlMillis)?.let { return it }
        lastCall = clock.nowMillis()  // a single device issues a handful of requests per lookup; far below SEC's 10 req/s
        val text = try { fetcher.text(url, headers) } catch (e: FetchException) {
            when {
                e.status == 404 -> throw EngineException.NoAnnualData("SEC has no XBRL company facts for this filer.")
                e.status == 403 || e.status >= 500 -> throw EngineException.UpstreamUnavailable("SEC EDGAR returned ${e.status}")
                e.status == 0 -> { ttl.getStale(url)?.let { return it }; throw EngineException.Offline() }
                else -> throw EngineException.UpstreamUnavailable(e.message ?: "fetch failed")
            }
        }
        val head = text.trimStart().firstOrNull()
        if (head != '{' && head != '[') throw EngineException.UpstreamUnavailable("SEC returned a non-JSON body (encoding or block page); not cached.")
        ttl.put(url, text)
        return text
    }

    fun masterList(): List<CompanyRef> {
        val text = tickersMirrorUrl?.let { runCatching { getText(it, 7 * DAY, emptyMap()) }.getOrNull() } ?: getText(TICKERS_URL, DAY)
        val root = Json.parseToJsonElement(text).jsonObject
        return root.values.map { row -> val o = row.jsonObject; CompanyRef(o["ticker"]!!.jsonPrimitive.content, o["cik_str"]!!.jsonPrimitive.content.toLong(), o["title"]!!.jsonPrimitive.content) }
    }

    fun resolve(ticker: String): CompanyRef {
        val wanted = normalizeTicker(ticker)
        return masterList().firstOrNull { it.ticker == wanted } ?: throw EngineException.UnknownTicker("'$wanted' is not a ticker in SEC's company list.")
    }

    fun search(query: String, limit: Int = 15): List<CompanyRef> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        val refs = masterList()
        val nq = normalizeTicker(q)
        val exact = refs.filter { it.ticker == nq }
        val prefix = refs.filter { it.ticker.startsWith(q) && it !in exact }
        val name = refs.filter { q in it.name.uppercase() && it !in exact && it !in prefix }
        return (exact + prefix + name).take(limit)
    }

    /** Filer profile from the submissions API (SIC, first filing, latest 10-K, successor notice). Cached 24 h. */
    fun profile(cik: Long): FilerProfile? = runCatching { FilerIdentity.parseProfile(getText(FilerIdentity.submissionsUrl(cik), DAY)) }.getOrNull()

    /**
     * Find the filer whose 10-K history a successor holding company inherited (see FilerIdentity).
     * Returns null when no candidate passes the evidence test.
     */
    fun findPredecessor(successor: CompanyRef, today: Day): Predecessor? = findPredecessor(successor, profile(successor.cik), today)

    fun findPredecessor(successor: CompanyRef, sp: FilerProfile?, today: Day): Predecessor? {
        if (sp == null || !sp.hasSuccessorNotice) return null  // no 8-K12B → never substitute (IPOs, spin-offs)
        val token = FilerIdentity.searchToken(sp.name.ifBlank { successor.name }) ?: return null
        // "ExxonMobil Holdings" vs "EXXON MOBIL CORP": retry with shorter prefixes so spacing differences still match.
        val seen = HashSet<Long>()
        for (prefix in FilerIdentity.searchPrefixes(token)) {
            val hits = runCatching { FilerIdentity.parseCandidates(getText(FilerIdentity.entitySearchUrl(prefix), DAY, mapOf("User-Agent" to userAgent)), successor.cik) }.getOrDefault(emptyList())
            for ((cik, name) in hits.take(8)) {
                if (!seen.add(cik)) continue
                val cp = profile(cik) ?: continue
                if (FilerIdentity.isPlausiblePredecessor(sp, cp, today))
                    return Predecessor(CompanyRef(successor.ticker, cik, cp.name.ifBlank { name }), sp.name, sp.firstFiling, sp.hasSuccessorNotice)
            }
        }
        return null
    }

    /** Raw submissions text (for the latest-filing lookup) — same cache entry the profile uses. */
    fun submissionsText(cik: Long): String? = runCatching { getText(FilerIdentity.submissionsUrl(cik), DAY) }.getOrNull()

    /** Inline-XBRL instance of a filing; large (1–8 MB) so cached for 30 days and fetched only on demand. */
    fun instance(ref: InstanceRef): String? = runCatching {
        val url = ref.instanceUrl
        ttl.get(url, 30 * DAY)?.let { return it }
        val text = fetcher.text(url, headers())
        if (!text.contains("<xbrl") && !text.contains("<xbrli:xbrl")) return null
        ttl.put(url, text); text
    }.getOrNull()

    fun companyFacts(ref: CompanyRef): CompanyFacts {
        val url = "https://data.sec.gov/api/xbrl/companyfacts/CIK${ref.cik.toString().padStart(10, '0')}.json"
        return CompanyFactsParser.parse(getText(url, DAY), CompanyRefCore(ref.ticker, ref.cik, ref.name))
    }
}
