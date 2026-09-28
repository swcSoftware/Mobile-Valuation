package com.swcsoftware.valuelens.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A private note the user wrote about one company (Sprint 9). Times are epoch milliseconds. */
@Serializable
data class CompanyNote(
    val id: String, val ticker: String,
    /** The company's display name when the note was written, so an export reads well offline. */
    val companyName: String,
    val text: String, val createdAt: Long, val updatedAt: Long,
)

/**
 * On-device notes, keyed by ticker, in the app's private files directory (notes.json); nothing is
 * sent anywhere. Mirrors iOS `NotesStore`: newest-edited first, blank text is not a note, clearing a
 * note deletes it, and removing a company from the watchlist keeps its notes.
 */
class NotesStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    var notes: List<CompanyNote> = runCatching { json.decodeFromString(ListSerializer(CompanyNote.serializer()), file.readText()) }.getOrDefault(emptyList())
        private set

    fun notesFor(ticker: String): List<CompanyNote> = notes.filter { it.ticker == ticker.uppercase() }.sortedByDescending { it.updatedAt }

    fun add(ticker: String, companyName: String, text: String, now: Long = System.currentTimeMillis()): CompanyNote? {
        val body = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val note = CompanyNote(UUID.randomUUID().toString(), ticker.uppercase(), companyName, body, now, now)
        notes = notes + note
        persist()
        return note
    }

    fun update(id: String, text: String, now: Long = System.currentTimeMillis()) {
        val old = notes.firstOrNull { it.id == id } ?: return
        val body = text.trim()
        notes = when {
            body.isEmpty() -> notes.filterNot { it.id == id }
            body == old.text -> return
            else -> notes.map { if (it.id == id) it.copy(text = body, updatedAt = now) else it }
        }
        persist()
    }

    fun delete(id: String) { notes = notes.filterNot { it.id == id }; persist() }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(ListSerializer(CompanyNote.serializer()), notes))
        tmp.renameTo(file)   // atomic on the same filesystem: a crash mid-write never leaves half a file
    }
}
