package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.Quote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.sqrt

/** Market data the filings cannot provide: price and beta. Keyless Yahoo chart endpoint (unofficial). */
class Market(private val fetcher: Fetcher, cache: KeyValueCache, private val clock: Clock) {
    private val ttl = TtlCache(cache, clock)
    private val json = Json { ignoreUnknownKeys = true }
    private val ua = mapOf("User-Agent" to "Mozilla/5.0 ValueLens/0.2")

    private fun chart(symbol: String, range: String, interval: String, ttlMillis: Long): String? {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/${symbol.uppercase().replace(".", "-")}?range=$range&interval=$interval"
        ttl.get(url, ttlMillis)?.let { return it }
        return runCatching { fetcher.text(url, ua) }.getOrNull()?.also { ttl.put(url, it) }
    }

    fun quote(ticker: String): Quote? {
        val text = chart(ticker, "1d", "1d", 15L * 60 * 1000) ?: return null
        return runCatching {
            val meta = json.parseToJsonElement(text).jsonObject["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject["meta"]!!.jsonObject
            val price = meta["regularMarketPrice"]!!.jsonPrimitive.double
            val ts = meta["regularMarketTime"]?.jsonPrimitive?.longOrNull
            Quote(ticker.uppercase(), price, meta["currency"]?.jsonPrimitive?.content ?: "USD", isoFromMillis((ts ?: clock.nowMillis() / 1000) * 1000), "yahoo")
        }.getOrNull()
    }

    /** Month-end adjusted closes keyed by "YYYY-MM". */
    private fun monthlyAdjClose(symbol: String): Map<String, Double>? {
        val text = chart(symbol, "5y", "1mo", 7 * Edgar.DAY) ?: return null
        return runCatching {
            val r = json.parseToJsonElement(text).jsonObject["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject
            val ts: List<Long> = r["timestamp"]!!.jsonArray.map { it.jsonPrimitive.long }
            val adj = r["indicators"]!!.jsonObject["adjclose"]!!.jsonArray[0].jsonObject["adjclose"]!!.jsonArray.map { it.jsonPrimitive.doubleOrNull }
            val out = LinkedHashMap<String, Double>()
            for (i in ts.indices) adj[i]?.let { out[yearMonth(ts[i] * 1000)] = it }
            out
        }.getOrNull()
    }

    /**
     * 5-year monthly regression beta vs the S&P 500 — the same convention Yahoo/most screeners use,
     * so users can reconcile the number. Needs ≥ 36 overlapping months.
     */
    fun beta(ticker: String): BetaResult? {
        val stock = monthlyAdjClose(ticker) ?: return null
        val index = monthlyAdjClose("^GSPC") ?: return null
        val months = stock.keys.filter { it in index }.sorted()
        val rs = mutableListOf<Double>(); val rm = mutableListOf<Double>()
        for (i in 1 until months.size) {
            val (a0, a1) = stock[months[i - 1]]!! to stock[months[i]]!!
            val (b0, b1) = index[months[i - 1]]!! to index[months[i]]!!
            if (a0 > 0 && b0 > 0) { rs += a1 / a0 - 1; rm += b1 / b0 - 1 }
        }
        val n = rs.size
        if (n < 36) return null
        val mx = rm.average(); val my = rs.average()
        var cov = 0.0; var vx = 0.0; var vy = 0.0
        for (i in 0 until n) { val dx = rm[i] - mx; val dy = rs[i] - my; cov += dx * dy; vx += dx * dx; vy += dy * dy }
        if (vx == 0.0) return null
        val beta = cov / vx
        val r2 = if (vy == 0.0) 0.0 else (cov / sqrt(vx * vy)).let { it * it }
        return BetaResult(beta, n, r2, "${months.first()}→${months.last()}", isoFromMillis(clock.nowMillis()))
    }

    private fun yearMonth(ms: Long): String { val d = Day(ms / Edgar.DAY); return "${d.year}-${d.month.toString().padStart(2, '0')}" }

    companion object {
        fun isoFromMillis(ms: Long): String {
            val day = Day(floorDiv(ms, Edgar.DAY))
            val rem = floorMod(ms, Edgar.DAY) / 1000
            val h = rem / 3600; val m = (rem % 3600) / 60; val s = rem % 60
            return "${day}T${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}+00:00"
        }
    }
}

@Serializable
data class BetaResult(val beta: Double, val months: Int, val r2: Double, val window: String, val computedAt: String)

/** Snapshot of `rates.json` published to GitHub Pages by the scheduled Action (see scripts/publish_rates.py). */
@Serializable
data class RatesSnapshot(
    val as_of: String, val published_at: String = "", val aaa_yield_pct: Double, val treasury_10y_pct: Double,
    val source: String = "FRED DAAA / DGS10",
) {
    /** Days between the observation date and `now`. */
    fun ageDays(nowMillis: Long): Long = Day.parse(as_of)?.let { Day(floorDiv(nowMillis, Edgar.DAY)).epochDays - it.epochDays } ?: Long.MAX_VALUE
    companion object {
        val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): RatesSnapshot? = runCatching { json.decodeFromString<RatesSnapshot>(text) }.getOrNull()
    }
}

object Rates {
    const val STALE_AFTER_DAYS = 7L
    /** Fetch the published rates file; falls back to the last cached copy (any age), then null. */
    fun fetch(fetcher: Fetcher, cache: KeyValueCache, clock: Clock, url: String): RatesSnapshot? {
        val ttl = TtlCache(cache, clock)
        val fresh = ttl.get(url, 12L * 3600 * 1000)
        val text = fresh ?: runCatching { fetcher.text(url, emptyMap()) }.getOrNull()?.also { ttl.put(url, it) } ?: ttl.getStale(url)
        return text?.let { RatesSnapshot.parse(it) }
    }
}

internal fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q }
internal fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
