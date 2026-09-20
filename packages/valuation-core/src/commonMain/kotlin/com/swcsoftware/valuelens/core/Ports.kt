package com.swcsoftware.valuelens.core

/** Blocking HTTP GET supplied by the host (OkHttp on Android, URLSession on iOS). */
interface Fetcher {
    /** Never throws: status 0 = no connection; body null on failure. Swift implementations can't throw Kotlin exceptions. */
    fun get(url: String, headers: Map<String, String>): FetchResult
}

class FetchResult(val status: Int, val body: String?) {
    val ok: Boolean get() = status in 200..299 && body != null
}

class FetchException(val status: Int, message: String) : Exception(message)

/** Adapter used inside the core: turns a FetchResult into text or a FetchException. */
internal fun Fetcher.text(url: String, headers: Map<String, String>): String {
    val r = get(url, headers)
    if (!r.ok) throw FetchException(r.status, "HTTP ${r.status}")
    return r.body!!
}

/** Simple persistent cache supplied by the host. Values are opaque strings; the core handles TTL. */
interface KeyValueCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** Epoch millis supplied by the host so tests can freeze time. */
fun interface Clock { fun nowMillis(): Long }
