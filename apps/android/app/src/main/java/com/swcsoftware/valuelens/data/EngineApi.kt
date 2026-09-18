package com.swcsoftware.valuelens.data

import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable private data class SearchResponse(val results: List<CompanyRef>)

/**
 * Talks to the valuation engine. Forwards the SEC identity as `X-SEC-User-Agent`,
 * exactly as the iOS APIClient does. Android emulator reaches the host at 10.0.2.2.
 */
class EngineApi(private val baseUrl: String = "http://10.0.2.2:8000", private val userAgent: String? = null) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/$path").apply { userAgent?.let { header("X-SEC-User-Agent", it) } }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Engine error ${resp.code}: ${resp.body?.string()}")
            resp.body!!.string()
        }
    }

    suspend fun search(query: String): List<CompanyRef> = json.decodeFromString<SearchResponse>(get("search?q=$query")).results
    suspend fun valuation(ticker: String): ValuationReport = json.decodeFromString(get("companies/$ticker/valuation"))
}
