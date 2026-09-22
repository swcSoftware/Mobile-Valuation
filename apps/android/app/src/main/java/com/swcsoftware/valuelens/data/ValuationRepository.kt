package com.swcsoftware.valuelens.data

import android.content.Context
import com.swcsoftware.valuelens.core.FetchResult
import com.swcsoftware.valuelens.core.Fetcher
import com.swcsoftware.valuelens.core.KeyValueCache
import com.swcsoftware.valuelens.core.RatesSnapshot
import com.swcsoftware.valuelens.core.ValuationCore
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface ValuationRepository {
    suspend fun search(query: String): List<CompanyRef>
    suspend fun valuation(ticker: String, priceOverride: Double? = null, overrides: RateOverrides = RateOverrides.NONE): ValuationReport
    suspend fun rates(): RatesSnapshot?
    /** Prefilled GitHub issue URL for a concept-map gap; the user reviews and submits it. */
    fun coverageIssueUrl(reportJson: String): String?
}

/** OkHttp-backed blocking fetcher for the core. */
class OkHttpFetcher : Fetcher {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    override fun get(url: String, headers: Map<String, String>): FetchResult {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val resp = try { client.newCall(req).execute() } catch (e: IOException) { return FetchResult(0, null) }
        resp.use { return FetchResult(it.code, if (it.isSuccessful) it.body?.string().orEmpty() else null) }
    }
}

/** One file per key under the app cache dir. */
class FileCache(context: Context) : KeyValueCache {
    private val dir = File(context.cacheDir, "valuelens-core").apply { mkdirs() }
    private fun file(key: String) = File(dir, MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })
    override fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()
    override fun put(key: String, value: String) { file(key).writeText(value) }
}

/** Bundled AAPL/KO/MSFT JSON in assets/ — the offline fallback, same files as the iOS bundle. */
class SampleValuationRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    companion object { val TICKERS = listOf("AAPL", "KO", "MSFT") }
    fun search(query: String): List<CompanyRef> {
        val q = query.trim().uppercase(); if (q.isEmpty()) return emptyList()
        return TICKERS.map { valuation(it).company }.filter { it.ticker.startsWith(q) || it.name.uppercase().contains(q) }
    }
    fun valuation(ticker: String): ValuationReport {
        val t = ticker.uppercase()
        if (t !in TICKERS) throw EngineException.UnknownTicker("'$t' is not bundled and the device is offline.")
        return json.decodeFromString(context.assets.open("$t.json").bufferedReader().use { it.readText() })
    }
}

/**
 * Runs the on-device valuation core. EDGAR, quotes and the published rates file are fetched
 * directly from the phone with the user's identity; bundled samples cover offline first runs.
 */
class CoreValuationRepository(context: Context, private val userAgent: String?) : ValuationRepository {
    private val fallback = SampleValuationRepository(context)
    private val core = ValuationCore(OkHttpFetcher(), FileCache(context), appVersion = "android ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"}", bundledRates = bundledRates(context))

    private fun bundledRates(context: Context): RatesSnapshot? =
        runCatching { RatesSnapshot.parse(context.assets.open("rates.json").bufferedReader().use { it.readText() }) }.getOrNull()

    private fun ua() = userAgent ?: throw EngineException.InvalidIdentity("Set your name and email in Settings so SEC EDGAR can identify you.")

    override suspend fun search(query: String): List<CompanyRef> = withContext(Dispatchers.IO) {
        try { core.search(query, ua()) } catch (e: EngineException.Offline) { fallback.search(query) } catch (e: Exception) { android.util.Log.w("ValueLens", "search failed", e); throw e }
    }

    override suspend fun valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides): ValuationReport = withContext(Dispatchers.IO) {
        try { core.valuation(ticker, ua(), priceOverride, overrides) } catch (e: EngineException.Offline) { android.util.Log.w("ValueLens", "valuation offline", e)
            if (ticker.uppercase() in SampleValuationRepository.TICKERS) fallback.valuation(ticker) else throw e
        }
    }

    override suspend fun rates(): RatesSnapshot? = withContext(Dispatchers.IO) { core.rates() }

    override fun coverageIssueUrl(reportJson: String): String? = runCatching { core.coverageIssueUrl(reportJson) }.getOrNull()
}
