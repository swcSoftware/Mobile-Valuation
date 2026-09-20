package com.swcsoftware.valuelens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swcsoftware.valuelens.data.AppPrefs
import com.swcsoftware.valuelens.data.EngineApi
import com.swcsoftware.valuelens.data.RemoteValuationRepository
import com.swcsoftware.valuelens.data.SampleValuationRepository
import com.swcsoftware.valuelens.data.SecureIdentityStore
import com.swcsoftware.valuelens.data.ValuationRepository
import com.swcsoftware.valuelens.data.WatchlistStore
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.SecIdentity
import com.swcsoftware.valuelens.domain.ValuationReport

/** App-wide observable state — the Android counterpart of iOS AppSettings + WatchlistStore + AppRouter. */
class AppState(app: Application) {
    private val identityStore = SecureIdentityStore(app)
    private val prefs = AppPrefs(app)
    private val sample = SampleValuationRepository(app)
    val watchlistStore = WatchlistStore(app)

    var identity: SecIdentity? by mutableStateOf(identityStore.load())
        private set
    var onboarded: Boolean by mutableStateOf(prefs.onboarded)
        private set
    var engineUrl: String by mutableStateOf(prefs.engineUrl)
        private set
    var overrides: RateOverrides by mutableStateOf(prefs.overrides)
        private set
    var engineReachable: Boolean by mutableStateOf(false)
    var engineLanAddresses: List<String> by mutableStateOf(emptyList())
    val watchlist = mutableStateListOf<com.swcsoftware.valuelens.data.WatchlistEntry>().apply { addAll(watchlistStore.entries) }
    /** Ticker requested via deep link (valuelens://ticker/AAPL) awaiting navigation. */
    var pendingTicker: String? by mutableStateOf(null)

    val repository: ValuationRepository
        get() = RemoteValuationRepository(EngineApi(engineUrl, identity?.userAgent), sample)

    fun saveIdentity(id: SecIdentity) { identityStore.save(id); identity = id }
    fun clearIdentity() { identityStore.clear(); identity = null; updateOnboarded(false) }
    fun updateOnboarded(v: Boolean) { prefs.onboarded = v; onboarded = v }
    fun updateEngineUrl(v: String) { prefs.engineUrl = v; engineUrl = v }
    fun updateOverrides(v: RateOverrides) { prefs.overrides = v; overrides = v }

    suspend fun checkEngine() {
        val h = repository.health()
        engineReachable = h != null
        engineLanAddresses = h?.lanAddresses ?: emptyList()
    }

    private fun syncWatchlist() { watchlist.clear(); watchlist.addAll(watchlistStore.entries) }
    fun addToWatchlist(r: ValuationReport) { watchlistStore.add(r); syncWatchlist() }
    fun updateWatchlist(r: ValuationReport) { watchlistStore.update(r); syncWatchlist() }
    fun removeFromWatchlist(ticker: String) { watchlistStore.remove(ticker); syncWatchlist() }
    fun inWatchlist(ticker: String) = watchlist.any { it.company.ticker == ticker }

    suspend fun refreshWatchlist() {
        val repo = repository
        for (e in watchlistStore.entries) {
            runCatching { repo.valuation(e.company.ticker, overrides = overrides) }.getOrNull()?.let { watchlistStore.update(it) }
        }
        syncWatchlist()
    }

    fun handleDeepLink(uri: android.net.Uri?): Boolean {
        if (uri?.scheme?.lowercase() != "valuelens") return false
        if (uri.host?.lowercase() !in setOf("ticker", "company")) return false
        val t = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return false
        pendingTicker = t.uppercase()
        return true
    }
}

class ValueLensApplication : Application() {
    lateinit var state: AppState
        private set
    override fun onCreate() { super.onCreate(); state = AppState(this) }
}
