package com.swcsoftware.valuelens.core

/** Blocking HTTP GET supplied by the host (OkHttp on Android, URLSession on iOS). */
interface Fetcher {
    /** Returns the body as UTF-8 text, or throws [FetchException]. */
    fun get(url: String, headers: Map<String, String>): String
}

class FetchException(val status: Int, message: String) : Exception(message)

/** Simple persistent cache supplied by the host. Values are opaque strings; the core handles TTL. */
interface KeyValueCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** Epoch millis supplied by the host so tests can freeze time. */
fun interface Clock { fun nowMillis(): Long }
