package com.swcsoftware.valuelens.data

import android.content.Context
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.EngineHealth
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.json.Json

interface ValuationRepository {
    suspend fun search(query: String): List<CompanyRef>
    suspend fun valuation(ticker: String, priceOverride: Double? = null, overrides: RateOverrides = RateOverrides.NONE): ValuationReport
    suspend fun health(): EngineHealth?
}

/** Bundled AAPL/KO/MSFT JSON in assets/ — the offline fallback, same files as the iOS bundle. */
class SampleValuationRepository(private val context: Context) : ValuationRepository {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    companion object { val TICKERS = listOf("AAPL", "KO", "MSFT") }

    override suspend fun search(query: String): List<CompanyRef> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        return TICKERS.map { valuation(it).company }.filter { it.ticker.startsWith(q) || it.name.uppercase().contains(q) }
    }

    override suspend fun valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides): ValuationReport {
        val t = ticker.uppercase()
        if (t !in TICKERS) throw EngineException.UnknownTicker("'$t' is not bundled; the engine is offline.")
        val text = context.assets.open("$t.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    override suspend fun health(): EngineHealth? = null
}

/** Talks to the engine; falls back to bundled samples when the engine is unreachable. */
class RemoteValuationRepository(private val api: EngineApi, private val fallback: SampleValuationRepository) : ValuationRepository {
    override suspend fun search(query: String): List<CompanyRef> =
        try { api.search(query) } catch (e: EngineException.Offline) { fallback.search(query) }

    override suspend fun valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides): ValuationReport =
        try { api.valuation(ticker, priceOverride, overrides) } catch (e: EngineException.Offline) {
            if (ticker.uppercase() in SampleValuationRepository.TICKERS) fallback.valuation(ticker, priceOverride, overrides) else throw e
        }

    override suspend fun health(): EngineHealth? = api.health()
}
