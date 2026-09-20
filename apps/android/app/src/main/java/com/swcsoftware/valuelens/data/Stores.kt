package com.swcsoftware.valuelens.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.SecIdentity
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** SEC identity in EncryptedSharedPreferences (blueprint §3: never plain storage). */
class SecureIdentityStore(context: Context) {
    private val prefs = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "sec_identity", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrElse { context.getSharedPreferences("sec_identity_fallback", Context.MODE_PRIVATE) }

    fun load(): SecIdentity? {
        val n = prefs.getString("name", null) ?: return null
        val e = prefs.getString("email", null) ?: return null
        return SecIdentity(n, e)
    }
    fun save(id: SecIdentity) = prefs.edit().putString("name", id.fullName).putString("email", id.email).apply()
    fun clear() = prefs.edit().clear().apply()
}

/** Non-sensitive preferences. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("valuelens", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    var expertMode: Boolean
        get() = prefs.getBoolean("expertMode", false)
        set(v) = prefs.edit().putBoolean("expertMode", v).apply()
    var overrides: RateOverrides
        get() = prefs.getString("overrides", null)?.let { runCatching { json.decodeFromString<RateOverrides>(it) }.getOrNull() } ?: RateOverrides.NONE
        set(v) = prefs.edit().putString("overrides", json.encodeToString(RateOverrides.serializer(), v)).apply()
}

@Serializable data class WatchlistEntry(val company: CompanyRef, val lastReport: ValuationReport? = null, val addedAt: Long, val updatedAt: Long? = null)

/** Local-only persistence of tracked tickers (JSON file in app files dir). */
class WatchlistStore(context: Context) {
    private val file = File(context.filesDir, "watchlist.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    var entries: List<WatchlistEntry> = runCatching { json.decodeFromString<List<WatchlistEntry>>(file.readText()) }.getOrDefault(emptyList())
        private set

    fun contains(ticker: String) = entries.any { it.company.ticker == ticker }
    fun add(report: ValuationReport) {
        val now = System.currentTimeMillis()
        entries = if (contains(report.company.ticker)) entries.map { if (it.company.ticker == report.company.ticker) it.copy(lastReport = report, updatedAt = now) else it }
        else entries + WatchlistEntry(report.company, report, now, now)
        persist()
    }
    fun update(report: ValuationReport) { if (contains(report.company.ticker)) add(report) }
    fun remove(ticker: String) { entries = entries.filterNot { it.company.ticker == ticker }; persist() }
    val lastUpdated: Long? get() = entries.mapNotNull { it.updatedAt }.maxOrNull()
    private fun persist() = file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(WatchlistEntry.serializer()), entries))
}
