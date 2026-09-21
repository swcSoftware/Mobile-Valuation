package com.swcsoftware.valuelens.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test

/**
 * Not a test: regenerates the bundled sample reports (AAPL/KO/MSFT) through the real core with
 * live network access. Runs only when VL_DUMP_DIR is set:
 *   VL_DUMP_DIR=/path SEC_USER_AGENT="Name email" ./gradlew :valuation-core:desktopTest --tests '*SampleDump*'
 */
class SampleDump {
    private class JvmFetcher : Fetcher {
        override fun get(url: String, headers: Map<String, String>): FetchResult {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 60000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            return try {
                val code = c.responseCode
                FetchResult(code, if (code in 200..299) c.inputStream.bufferedReader().readText() else null)
            } catch (e: Exception) { FetchResult(0, null) }
        }
    }

    /** VL_PROBE_TICKERS="XOM,JPM" prints a one-line summary per ticker through the real core (live network). */
    @Test fun probeTickersIfRequested() {
        val tickers = System.getenv("VL_PROBE_TICKERS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val ua = System.getenv("SEC_USER_AGENT") ?: "ValueLens Dev dev@example.com"
        val core = ValuationCore(JvmFetcher(), MemCache())
        for (t in tickers) {
            val line = try {
                val r = core.valuation(t, ua)
                val fails = r.dataChecks.filter { it.status == "fail" }.map { it.key }; val warns = r.dataChecks.filter { it.status == "warn" }.map { it.key }
                "$t: ${r.company.name} (CIK ${r.company.cik}) price=${r.quote?.price} A=${r.modelA.intrinsicValuePerShare} B=${r.modelB.intrinsicValuePerShare} verdict=${r.modelA.marginOfSafety.verdict} beta=${r.assumptions.beta}(${r.provenance["beta"]}) fails=$fails warns=$warns"
            } catch (e: Exception) { "$t: ERROR ${e::class.simpleName}: ${e.message}" }
            println("PROBE $line")
        }
    }

    @Test fun dumpSamplesIfRequested() {
        val dir = System.getenv("VL_DUMP_DIR") ?: return
        val ua = System.getenv("SEC_USER_AGENT") ?: "ValueLens Dev dev@example.com"
        val core = ValuationCore(JvmFetcher(), MemCache())
        for (t in listOf("AAPL", "KO", "MSFT")) {
            val json = core.valuationJson(t, ua)
            File(dir, "$t.json").writeText(json)
            println("dumped $t (${json.length} bytes)")
        }
    }
}
