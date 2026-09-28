package com.swcsoftware.valuelens.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swcsoftware.valuelens.domain.FilingDocument
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads every SEC filing behind the company being viewed into the app's cache, and deletes them when the
 * page closes (Sprint 9; mirror of iOS `FilingsDownloader`). One at a time, 200 ms apart, under the user's own
 * SEC identity. OkHttp negotiates gzip itself, so a 12 MB 10-K is under 1 MB over the network.
 */
class FilingsDownloader(private val cacheRoot: File) {
    sealed interface Status { data object Waiting : Status; data object Downloading : Status; data class Done(val bytes: Long) : Status; data object Failed : Status; data object NoDocument : Status }

    val documents = mutableStateListOf<FilingDocument>()
    val status = mutableStateMapOf<String, Status>()
    var ticker: String? by mutableStateOf(null)
        private set

    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    private fun dir(t: String) = File(File(cacheRoot, "filings"), t)

    /** Run inside the page's effect: cancelling the effect (leaving the page) stops the downloads. */
    suspend fun run(report: ValuationReport, repository: ValuationRepository, userAgent: String?) {
        discard()
        val t = report.company.ticker; ticker = t
        val docs = repository.filingDocuments(report)
        documents.addAll(docs)
        docs.forEach { status[it.filing.accession] = if (it.documentUrl == null) Status.NoDocument else Status.Waiting }
        val dir = dir(t).apply { mkdirs() }
        for (d in docs) {
            coroutineContext.ensureActive()
            val url = d.documentUrl ?: continue
            if (userAgent == null) continue
            status[d.filing.accession] = Status.Downloading
            status[d.filing.accession] = withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val out = File(dir, "${d.filing.accession}.htm")
                        resp.body!!.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
                        Status.Done(out.length())
                    }
                }.getOrElse { Status.Failed }
            }
            delay(200)
        }
    }

    fun localFile(d: FilingDocument): File? = ticker?.takeIf { status[d.filing.accession] is Status.Done }?.let { File(dir(it), "${d.filing.accession}.htm") }
    val downloadedCount get() = status.values.count { it is Status.Done }
    val downloadableCount get() = documents.count { it.documentUrl != null }
    val downloadedBytes get() = status.values.sumOf { (it as? Status.Done)?.bytes ?: 0L }
    val isFinished get() = documents.isNotEmpty() && status.values.none { it == Status.Waiting || it == Status.Downloading }

    /** Deletes this company's files. Called when the company page closes. */
    fun discard() {
        ticker?.let { dir(it).deleteRecursively() }
        documents.clear(); status.clear(); ticker = null
    }

    companion object {
        /** Deletes anything a crash or a killed app left behind. Called at launch. */
        fun sweep(cacheRoot: File) { File(cacheRoot, "filings").deleteRecursively() }
    }
}
