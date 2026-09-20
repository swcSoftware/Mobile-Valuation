package com.swcsoftware.valuelens.data

import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.EngineHealth
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable private data class SearchResponse(val results: List<CompanyRef>)

/**
 * HTTP client for the valuation engine. Forwards the SEC identity as `X-SEC-User-Agent`,
 * exactly as the iOS APIClient does. Emulator reaches the host at 10.0.2.2.
 */
class EngineApi(private val baseUrl: String, private val userAgent: String?) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: throw EngineException.Offline()
        val url = base.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).apply { userAgent?.let { header("X-SEC-User-Agent", it) } }.build()
        val resp = try { client.newCall(req).execute() } catch (e: IOException) { throw EngineException.Offline() }
        resp.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw EngineException.from(it.code, body, json)
            body
        }
    }

    suspend fun search(query: String, limit: Int = 15): List<CompanyRef> =
        json.decodeFromString<SearchResponse>(get("search", mapOf("q" to query, "limit" to limit.toString()))).results

    suspend fun valuation(ticker: String, priceOverride: Double? = null, overrides: RateOverrides = RateOverrides.NONE): ValuationReport {
        val q = overrides.queryParams().toMutableMap()
        priceOverride?.let { q["price"] = it.toString() }
        return json.decodeFromString(get("companies/$ticker/valuation", q))
    }

    suspend fun health(): EngineHealth? = runCatching { json.decodeFromString<EngineHealth>(get("health")) }.getOrNull()
}
